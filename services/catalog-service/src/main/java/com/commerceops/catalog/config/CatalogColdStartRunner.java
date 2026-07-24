package com.commerceops.catalog.config;

import com.commerceops.catalog.search.CatalogSearchIndexService;
import com.commerceops.catalog.service.CatalogProjectionService;
import com.commerceops.catalog.strapi.StrapiSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogColdStartRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogColdStartRunner.class);

    private final CatalogProjectionService projectionService;
    private final StrapiSyncService strapiSyncService;
    private final CatalogSearchIndexService indexService;

    public CatalogColdStartRunner(
            CatalogProjectionService projectionService,
            StrapiSyncService strapiSyncService,
            CatalogSearchIndexService indexService) {
        this.projectionService = projectionService;
        this.strapiSyncService = strapiSyncService;
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            projectionService.bootstrapFromInventoryIfEmpty();
            indexService.ensureIndex();
            strapiSyncService.syncAllPublished();
            indexService.reindexIfEmpty();
        } catch (Exception ex) {
            log.warn("Catalog startup sync failed: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${commerce.catalog.strapi-sync-ms:60000}")
    public void periodicStrapiSync() {
        try {
            strapiSyncService.syncAllPublished();
        } catch (Exception ex) {
            log.debug("Periodic Strapi sync failed: {}", ex.getMessage());
        }
    }
}
