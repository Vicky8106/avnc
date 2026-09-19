/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.model

import androidx.room.testing.MigrationTestHelper
import com.vncandroid.free.instrumentation
import com.vncandroid.free.model.db.MainDb
import org.junit.Rule
import org.junit.Test

class DatabaseTest {
    private val dbName = "Bond. James Bond."
    private val minVersion = 1
    private val maxVersion = MainDb.VERSION

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MainDb::class.java)

    @Test
    fun migrations() {
        for (i in minVersion until maxVersion)
            for (j in i + 1..maxVersion)
                runCatching {
                    helper.createDatabase(dbName, i).close()
                    helper.runMigrationsAndValidate(dbName, j, false).close()
                }.onFailure {
                    throw Exception("Failed to migrate MainDb from [$i] to [$j]", it)
                }
    }
}