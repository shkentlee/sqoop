/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sqoop.manager.mysql;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

import org.apache.sqoop.SqoopOptions;
import org.apache.sqoop.testutil.CommonArgs;
import org.apache.sqoop.testutil.ExportJobTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test free form query import with the MySQL db.
 */
public class MySqlCallExportTest extends ExportJobTestCase {

  public static final Log LOG = LogFactory.getLog(
      MySqlCallExportTest.class.getName());

  private final String tableName = "MYSQL_CALL_EXPORT_BASE_TABLE";
  private final String procName = "MYSQL_CALL_EXPORT_PROC";
  private MySQLTestUtils mySQLTestUtils = new MySQLTestUtils();

  @Before
  public void setUp() {
    super.setUp();
    createObjects();
  }

  @After
  public void tearDown() {
    try {
      Statement stmt = getManager().getConnection().createStatement();
      stmt.execute("DROP TABLE " + getTableName());
    } catch(SQLException e) {
      LOG.error("Can't clean up the database:", e);
    }

    super.tearDown();
  }

  private String[] getArgv(String... extraArgs) {
    ArrayList<String> args = new ArrayList<String>();

    CommonArgs.addHadoopFlags(args);

    args.add("--call");
    args.add(procName);
    args.add("--export-dir");
    args.add(getWarehouseDir());
    args.add("--fields-terminated-by");
    args.add(",");
    args.add("--lines-terminated-by");
    args.add("\\n");
    args.add("--connect");
    args.add(getConnectString());
    args.add("-m");
    args.add("1");

    for (String arg : extraArgs) {
      args.add(arg);
    }

    return args.toArray(new String[0]);
  }

  private void createObjects() {

    String createTableSql = "CREATE TABLE " + tableName + " ( "
      + "id  INT NOT NULL PRIMARY KEY, "
      + "msg VARCHAR(24) NOT NULL, "
      + "d DATE, "
      + "f FLOAT, "
      + "vc VARCHAR(32))";

    String createProcSql = "CREATE PROCEDURE " + procName + " ( "
      + "IN  id INT,"
      + "IN msg VARCHAR(24),"
      + "IN d DATE,"
      + "IN f FLOAT) BEGIN "
      + "INSERT INTO " + tableName + " "
      + "VALUES(id,"
      + "msg,"
      + "d,"
      + "f,"
      + "concat(msg, '_2')); END";

    try {
      dropTableIfExists(tableName);
      dropProcedureIfExists(procName);
    } catch (SQLException sqle) {
      throw new AssertionError(sqle.getMessage());
    }
    Connection conn = getConnection();

    try {
      Statement st = conn.createStatement();
      st.executeUpdate(createTableSql);
      LOG.debug("Successfully created table " + tableName);
      st.executeUpdate(createProcSql);
      LOG.debug("Successfully created procedure " + procName);
      st.close();
    } catch (SQLException sqle) {
      throw new AssertionError(sqle.getMessage());
    }
  }

  @Override
  protected Connection getConnection() {
    try {
      return getManager().getConnection();
    } catch (SQLException sqle) {
      throw new AssertionError(sqle.getMessage());
    }
  }

  @Override
  protected boolean useHsqldbTestServer() {
    return false;
  }

  @Override
  protected String getConnectString() {
    return mySQLTestUtils.getMySqlConnectString();
  }

  @Override
  protected SqoopOptions getSqoopOptions(Configuration conf) {
    SqoopOptions opts = new SqoopOptions(conf);
    opts.setUsername(mySQLTestUtils.getUserName());
    mySQLTestUtils.addPasswordIfIsSet(opts);
    return opts;
  }

  @Override
  protected String getTableName() {
    return tableName;
  }

  @Override
  protected void dropTableIfExists(String table) throws SQLException {
    Connection conn = getManager().getConnection();
    PreparedStatement statement = conn.prepareStatement(
        "DROP TABLE IF EXISTS " + table,
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    try {
      statement.executeUpdate();
      conn.commit();
    } finally {
      statement.close();
    }
  }

  protected void dropProcedureIfExists(String proc) throws SQLException {
    Connection conn = getManager().getConnection();
    PreparedStatement statement = conn.prepareStatement(
      "DROP PROCEDURE IF EXISTS " + proc,
      ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    try {
      statement.executeUpdate();
      conn.commit();
    } finally {
      statement.close();
    }
  }

  @Test
  public void testExportUsingProcedure() throws IOException, SQLException {
    String[] lines = {
      "0,textfield0,2002-12-29,3300",
      "1,textfield1,2007-06-04,4400",
    };
    new File(getWarehouseDir()).mkdirs();
    File file = new File(getWarehouseDir() + "/part-00000");
    Writer output = new BufferedWriter(new FileWriter(file));
    for (String line : lines) {
      output.write(line);
      output.write("\n");
    }
    output.close();
    runExport(getArgv());
    verifyExport(2, getConnection());
  }
}
