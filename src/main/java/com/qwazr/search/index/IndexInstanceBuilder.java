/*
 * Copyright 2015-2018 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.index;

import com.qwazr.search.analysis.AnalyzerContext;
import com.qwazr.search.analysis.AnalyzerFactory;
import com.qwazr.search.analysis.CustomAnalyzer;
import com.qwazr.search.analysis.UpdatableAnalyzers;
import com.qwazr.search.field.FieldDefinition;
import com.qwazr.server.ServerException;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.concurrent.ReadWriteSemaphores;
import com.qwazr.utils.reflection.ConstructorParametersImpl;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.index.NoMergeScheduler;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SimpleMergedSegmentWarmer;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NRTCachingDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.InfoStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

class IndexInstanceBuilder {

    final IndexFileSet fileSet;
    final ExecutorService executorService;
    final ReadWriteSemaphores readWriteSemaphores;
    final IndexInstance.Provider indexProvider;

    private final IndexServiceInterface indexService;

    final IndexSettingsDefinition settings;
    final ConstructorParametersImpl instanceFactory;
    final FileResourceLoader fileResourceLoader;
    final UUID indexUuid;
    final String indexName;

    Directory dataDirectory;
    Directory taxonomyDirectory;

    private IndexWriter indexWriter;
    private SnapshotDirectoryTaxonomyWriter taxonomyWriter;

    final Map<String, AnalyzerFactory> globalAnalyzerFactoryMap;
    LinkedHashMap<String, CustomAnalyzer.Factory> localAnalyzerFactoryMap;

    FieldMap fieldMap = null;

    UpdatableAnalyzers indexAnalyzers;
    UpdatableAnalyzers queryAnalyzers;

    ReplicationMaster replicationMaster;
    ReplicationSlave replicationSlave;
    WriterAndSearcher writerAndSearcher = null;

    private Similarity similarity;
    private SearcherFactory searcherFactory;

    IndexInstanceBuilder(final IndexInstance.Provider indexProvider, final ConstructorParametersImpl instanceFactory,
            final Map<String, AnalyzerFactory> globalAnalyzerFactoryMap, final ReadWriteSemaphores readWriteSemaphores,
            final ExecutorService executorService, final IndexServiceInterface indexService, final IndexFileSet fileSet,
            final IndexSettingsDefinition settings, final UUID indexUuid, final String indexName) {
        this.fileSet = fileSet;
        this.executorService = executorService;
        this.readWriteSemaphores = readWriteSemaphores;
        this.indexProvider = indexProvider;
        this.instanceFactory = instanceFactory;
        this.settings = settings;
        this.globalAnalyzerFactoryMap = globalAnalyzerFactoryMap;
        this.indexService = indexService;
        this.fileResourceLoader = new FileResourceLoader(null, fileSet.resourcesDirectoryPath);
        this.indexUuid = indexUuid;
        this.indexName = indexName;
    }

    private void buildCommon() throws IOException, ReflectiveOperationException {

        if (settings.similarityClass != null && !settings.similarityClass.isEmpty())
            similarity = IndexUtils.findSimilarity(instanceFactory, settings.similarityClass);

        searcherFactory = MultiThreadSearcherFactory.of(executorService,
                settings.indexReaderWarmer == null ? true : settings.indexReaderWarmer, similarity,
                settings.sortedSetFacetField);

        localAnalyzerFactoryMap = fileSet.loadAnalyzerDefinitionMap();
        final LinkedHashMap<String, FieldDefinition> fieldMapDefinition = fileSet.loadFieldMap();

        fieldMap = fieldMapDefinition == null ? null : new FieldMap(fieldMapDefinition, settings.sortedSetFacetField);

        final AnalyzerContext context =
                new AnalyzerContext(instanceFactory, fileResourceLoader, fieldMap, false, globalAnalyzerFactoryMap,
                        localAnalyzerFactoryMap);
        indexAnalyzers = new UpdatableAnalyzers(context.indexAnalyzerMap);
        queryAnalyzers = new UpdatableAnalyzers(context.queryAnalyzerMap);

        // Open and lock the index directories
        dataDirectory = getDirectory(settings, fileSet.dataDirectory);
        taxonomyDirectory = IndexSettingsDefinition.useTaxonomyIndex(settings) ?
                getDirectory(settings, fileSet.taxonomyDirectory) :
                null;
    }

    static Directory getDirectory(IndexSettingsDefinition settings, Path dataDirectory) throws IOException {
        final Directory directory = settings == null || settings.directoryType == null ||
                settings.directoryType == IndexSettingsDefinition.Type.FSDirectory ?
                FSDirectory.open(dataDirectory) :
                new RAMDirectory();
        final double maxMergeSizeMB = settings == null || settings.nrtCachingDirectoryMaxMergeSizeMB == null ?
                IndexSettingsDefinition.DEFAULT_NRT_CACHING_DIRECTORY_MERGE_SIZE_MB :
                settings.nrtCachingDirectoryMaxMergeSizeMB;
        final double maxCacheMB = settings == null || settings.nrtCachingDirectoryMaxCachedMB == null ?
                IndexSettingsDefinition.DEFAULT_NRT_CACHING_DIRECTORY_MAX_CACHED_MB :
                settings.nrtCachingDirectoryMaxCachedMB;
        if (maxMergeSizeMB == 0 || maxCacheMB == 0)
            return directory;
        return new NRTCachingDirectory(directory, maxMergeSizeMB, maxCacheMB);
    }

    private final static int MERGE_SCHEDULER_SSD_THREADS =
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private void openOrCreateDataIndex(boolean closeAfter) throws IOException {

        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(indexAnalyzers);
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        if (settings != null) {
            if (similarity != null)
                indexWriterConfig.setSimilarity(similarity);
            if (settings.ramBufferSize != null)
                indexWriterConfig.setRAMBufferSizeMB(settings.ramBufferSize);
            if (settings.useCompoundFile != null)
                indexWriterConfig.setUseCompoundFile(settings.useCompoundFile);
            if(settings.useSimpleTextCodec != null && settings.useSimpleTextCodec)
                indexWriterConfig.setCodec(new SimpleTextCodec());

            final TieredMergePolicy mergePolicy = new TieredMergePolicy();
            if (settings.maxMergeAtOnce != null)
                mergePolicy.setMaxMergeAtOnce(settings.maxMergeAtOnce);
            if (settings.maxMergedSegmentMB != null)
                mergePolicy.setMaxMergedSegmentMB(settings.maxMergedSegmentMB);
            if (settings.segmentsPerTier != null)
                mergePolicy.setSegmentsPerTier(settings.segmentsPerTier);
            indexWriterConfig.setMergePolicy(mergePolicy);

            if (settings.mergedSegmentWarmer != null && settings.mergedSegmentWarmer)
                indexWriterConfig.setMergedSegmentWarmer(new SimpleMergedSegmentWarmer(InfoStream.getDefault()));

            final MergeScheduler mergeScheduler;
            if (settings.mergeScheduler != null) {
                switch (settings.mergeScheduler) {
                case NO:
                    mergeScheduler = NoMergeScheduler.INSTANCE;
                    break;
                case CONCURRENT:
                    mergeScheduler = new ConcurrentMergeScheduler();
                    ((ConcurrentMergeScheduler) mergeScheduler).setMaxMergesAndThreads(MERGE_SCHEDULER_SSD_THREADS,
                            MERGE_SCHEDULER_SSD_THREADS);
                    break;
                default:
                case SERIAL:
                    mergeScheduler = new SerialMergeScheduler();
                    break;
                }
                indexWriterConfig.setMergeScheduler(mergeScheduler);
            }
        }

        final SnapshotDeletionPolicy snapshotDeletionPolicy =
                new SnapshotDeletionPolicy(indexWriterConfig.getIndexDeletionPolicy());
        indexWriterConfig.setIndexDeletionPolicy(snapshotDeletionPolicy);

        indexWriter = checkCommit(new IndexWriter(dataDirectory, indexWriterConfig));
        if (closeAfter) {
            IOUtils.closeQuietly(indexWriter);
            indexWriter = null;
        }
    }

    private IndexWriter checkCommit(final IndexWriter indexWriter) throws IOException {
        if (indexWriter.hasUncommittedChanges())
            indexWriter.commit();
        return indexWriter;
    }

    private void openOrCreateTaxonomyIndex(boolean closeAfter) throws IOException {
        taxonomyWriter = new SnapshotDirectoryTaxonomyWriter(taxonomyDirectory);
        checkCommit(taxonomyWriter.getIndexWriter());
        if (closeAfter) {
            IOUtils.closeQuietly(taxonomyWriter);
            taxonomyWriter = null;
        }
    }

    private void buildSlave() throws IOException {

        openOrCreateDataIndex(true);

        if (IndexSettingsDefinition.useTaxonomyIndex(settings)) {
            openOrCreateTaxonomyIndex(true);
            replicationSlave = ReplicationSlave.withIndexAndTaxo(fileSet, indexService, settings.master, dataDirectory,
                    taxonomyDirectory);
            writerAndSearcher = new WriterAndSearcher.WithIndexAndTaxo(null, null,
                    () -> new SearcherTaxonomyManager(dataDirectory, taxonomyDirectory, searcherFactory));
        } else {
            replicationSlave = ReplicationSlave.withIndex(fileSet, indexService, settings.master, dataDirectory);
            writerAndSearcher =
                    new WriterAndSearcher.WithIndex(null, () -> new SearcherManager(dataDirectory, searcherFactory));
        }

    }

    private void buildMaster() throws IOException {

        openOrCreateDataIndex(false);

        if (IndexSettingsDefinition.useTaxonomyIndex(settings)) {
            openOrCreateTaxonomyIndex(false);
            replicationMaster =
                    new ReplicationMaster.WithIndexAndTaxo(indexUuid.toString(), fileSet, indexWriter, taxonomyWriter);
            writerAndSearcher = new WriterAndSearcher.WithIndexAndTaxo(indexWriter, taxonomyWriter,
                    () -> new SearcherTaxonomyManager(indexWriter, true, searcherFactory, taxonomyWriter));
        } else {
            replicationMaster = new ReplicationMaster.WithIndex(indexUuid.toString(), fileSet, indexWriter);
            writerAndSearcher = new WriterAndSearcher.WithIndex(indexWriter,
                    () -> new SearcherManager(indexWriter, searcherFactory));
        }
    }

    private void abort() {
        IOUtils.closeQuietly(writerAndSearcher, replicationMaster, indexAnalyzers, queryAnalyzers);

        if (taxonomyWriter != null) {
            IOUtils.closeQuietly(taxonomyWriter);
            taxonomyWriter = null;
        }

        if (taxonomyDirectory != null) {
            IOUtils.closeQuietly(taxonomyDirectory);
            taxonomyDirectory = null;
        }

        if (indexWriter != null && indexWriter.isOpen()) {
            IOUtils.closeQuietly(indexWriter);
            indexWriter = null;
        }

        if (dataDirectory != null) {
            IOUtils.closeQuietly(dataDirectory);
            dataDirectory = null;
        }
    }

    IndexInstance build() throws ReflectiveOperationException, IOException {
        try {
            buildCommon();
            if (settings.master != null && settings.master.schema != null && settings.master.index != null)
                buildSlave();
            else
                buildMaster();
            return new IndexInstance(this);
        } catch (IOException | ReflectiveOperationException e) {
            abort();
            throw e;
        } catch (Exception e) {
            abort();
            throw ServerException.of(e);
        }
    }

}
