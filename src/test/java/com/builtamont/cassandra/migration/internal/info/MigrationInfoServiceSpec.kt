/**
 * File     : MigrationInfoServiceSpec.kt
 * License  :
 *   Original   - Copyright (c) 2015 - 2016 Contrast Security
 *   Derivative - Copyright (c) 2016 - 2017 Citadel Technology Solutions Pte Ltd
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.builtamont.cassandra.migration.internal.info

import com.builtamont.cassandra.migration.api.MigrationState
import com.builtamont.cassandra.migration.api.MigrationType
import com.builtamont.cassandra.migration.api.MigrationVersion
import com.builtamont.cassandra.migration.api.resolver.MigrationResolver
import com.builtamont.cassandra.migration.api.resolver.ResolvedMigration
import com.builtamont.cassandra.migration.internal.dbsupport.SchemaVersionDAO
import com.builtamont.cassandra.migration.internal.metadatatable.AppliedMigration
import com.builtamont.cassandra.migration.internal.resolver.ResolvedMigrationImpl
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.kotlintest.specs.FreeSpec
import java.util.*

/**
 * MigrationInfoServiceSpec unit tests.
 */
class MigrationInfoServiceSpec : FreeSpec() {

    /**
     * Create a new available migration with the given version.
     *
     * @param version The migration version.
     * @return The available migration.
     */
    fun createAvailableMigration(version: String): ResolvedMigration {
        val migration = ResolvedMigrationImpl()
        migration.version = MigrationVersion.fromVersion(version)
        migration.description = "abc very very very very very very very very very very long"
        migration.script = "x"
        migration.type = MigrationType.CQL
        return migration
    }

    /**
     * Creates a new applied migration with this version.
     *
     * @param version     The version of the migration.
     * @param description The description of the migration.
     * @return The applied migration.
     */
    fun createAppliedMigration(version: String, description: String = "abc"): AppliedMigration {
        return AppliedMigration(
                version.toInt(),
                version.toInt(),
                MigrationVersion.fromVersion(version),
                description,
                MigrationType.CQL,
                "x",
                null,
                Date(),
                "sa",
                100,
                success = true
        )
    }

    /**
     * Creates a new applied baseline migration with this version.
     *
     * @param version The version of the migration.
     * @return The applied baseline migration.
     */
    fun createAppliedInitMigration(version: String, description: String = "abc"): AppliedMigration {
        return AppliedMigration(
                version.toInt(),
                version.toInt(),
                MigrationVersion.fromVersion(version),
                description,
                MigrationType.BASELINE,
                "x",
                null,
                Date(),
                "sa",
                100,
                success = true
        )
    }

    /**
     * Creates a new applied schema migration with this version.
     *
     * @return The applied schema migration.
     */
    fun createAppliedSchemaMigration(): AppliedMigration {
        return AppliedMigration(
                0,
                0,
                MigrationVersion.fromVersion("0"),
                "<< Schema Creation >>",
                MigrationType.SCHEMA,
                "x",
                null,
                Date(),
                "sa",
                100,
                success = true
        )
    }

    /**
     * Creates a MigrationResolver for testing.
     *
     * @param resolvedMigrations The resolved migrations.
     * @return The migration resolver.
     */
    fun createMigrationResolver(vararg resolvedMigrations: ResolvedMigration): MigrationResolver {
        return object : MigrationResolver {
            override fun resolveMigrations(): List<ResolvedMigration> {
                return resolvedMigrations.toList()
            }
        }
    }

    /**
     * Create mocked SchemaVersionDAO for testing.
     *
     * @return The mocked SchemaVersionDAO.
     */
    fun createSchemaVersionDAO(vararg appliedMigrations: AppliedMigration): SchemaVersionDAO {
        val migrations = if (appliedMigrations.isEmpty()) {
            arrayListOf<AppliedMigration>()
        } else {
            appliedMigrations.toList()
        }
        return mock {
            on { findAppliedMigrations() } doReturn migrations
        }
    }

    init {

        "MigrationInfoService" - {

            "should read pending migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1"), createAvailableMigration("2")),
                        createSchemaVersionDAO(),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current() shouldBe null
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 2
            }

            "should read all applied migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1"), createAvailableMigration("2")),
                        createSchemaVersionDAO(createAppliedMigration("1"), createAppliedMigration("2")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "2"
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

            "should read overridden applied migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1")),
                        createSchemaVersionDAO(createAppliedMigration("1", "xyz")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "1"
                migrationInfoService.current()!!.description shouldBe "xyz"
                migrationInfoService.all().size shouldBe 1
                migrationInfoService.pending().size shouldBe 0
            }

            "should read one pending and one applied migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1"), createAvailableMigration("2")),
                        createSchemaVersionDAO(createAppliedMigration("1")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "1"
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 1
            }

            "should read one applied and one skipped migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1"), createAvailableMigration("2")),
                        createSchemaVersionDAO(createAppliedMigration("2")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "2"
                migrationInfoService.all().first().state shouldBe MigrationState.IGNORED
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

            "should read two applied and one future migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1")),
                        createSchemaVersionDAO(createAppliedMigration("1"), createAppliedMigration("2")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "2"
                migrationInfoService.current()!!.state shouldBe MigrationState.FUTURE_SUCCESS
                migrationInfoService.future().first().state shouldBe MigrationState.FUTURE_SUCCESS
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

            "should read below baseline migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1")),
                        createSchemaVersionDAO(createAppliedInitMigration("2")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "2"
                migrationInfoService.all().first().state shouldBe MigrationState.BELOW_BASELINE
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

            "should read missing migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("2")),
                        createSchemaVersionDAO(createAppliedMigration("1"), createAppliedMigration("2")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "2"
                migrationInfoService.all().first().state shouldBe MigrationState.MISSING_SUCCESS
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

            "should read schema creation migrations info" {
                val migrationInfoService = MigrationInfoServiceImpl(
                        createMigrationResolver(createAvailableMigration("1")),
                        createSchemaVersionDAO(createAppliedSchemaMigration(), createAppliedMigration("1")),
                        MigrationVersion.LATEST,
                        outOfOrder = false,
                        pendingOrFuture = true
                )
                migrationInfoService.refresh()

                migrationInfoService.current()!!.version.toString() shouldBe "1"
                migrationInfoService.all().first().state shouldBe MigrationState.SUCCESS
                migrationInfoService.all()[1].state shouldBe MigrationState.SUCCESS
                migrationInfoService.all().size shouldBe 2
                migrationInfoService.pending().size shouldBe 0
            }

        }

    }

}
