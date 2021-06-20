package com.dbcomparer.app;

import com.mysql.cj.jdbc.ConnectionImpl;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbcomparerApplicationTests {

    @Value("${userdb}")
    private String username;
    @Value("${password}")
    private String password;
    @Value("${url}")
    private String url;
    private Connection prod;
    private Connection dev;
    private Connection files;
    private boolean indicesMinimal = true;

    private Connection getConnetion(String dbName) {
        try {
            String tempUrl = url;
            tempUrl = tempUrl.replace("dbname", dbName);
            System.out.println(tempUrl + " " + username + " " + password);
            Connection connection = DriverManager.getConnection(
                    tempUrl,
                    username,
                    password
            );
            return connection;
        } catch (SQLException e) {
            System.err.println(e);
        }
        return null;
    }

    @Test
    @Order(1)
    void connectionToDatabases() throws Exception {
        // Connection
        this.prod = getConnetion("mysocialplans");
        this.dev = getConnetion("mysocialplans_dev");
        this.files = getConnetion("mysocialplans_files");
        assert !prod.isClosed();
        assert !dev.isClosed();
        assert !files.isClosed();
    }

    /**
     * ******************************************************************************************************************************************************************************************************************************
     */

    private List<String> tables;

    List<String> getTables(Connection db) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement pps = db.prepareStatement("show tables");
            ResultSet rs = pps.executeQuery();
            while (rs != null && rs.next()) {
                list.add(rs.getString("Tables_in_" + ((ConnectionImpl) (db)).getDatabase()));
            }
        } catch (Exception e) {
            System.err.println(e);
            assert e.toString().equals("");
        }
        return list;
    }

    boolean compareTablesDatabases(List<String> dbA, List<String> dbB) {
        if (dbA.size() != dbB.size()) {
            return false;
        }
        Collections.sort(dbA);
        Collections.sort(dbB);
        for (int i = 0; i < dbA.size(); i++) {
            if (!dbA.get(i).equals(dbB.get(i))) {
                System.err.println(dbA.get(i) + " vs " + dbB.get(i));
                return false;
            }
        }
        return true;
    }

    @Test
    @Order(2)
    void compareTables() throws Exception {
        //Tables
        this.connectionToDatabases();
        tables = getTables(prod);
        List<String> tablesDev = getTables(dev);
        List<String> tablesFiles = getTables(files);
        System.out.println("PROD vs DEV");
        assert compareTablesDatabases(tablesFiles, tablesDev);
        System.out.println("DEV vs FILES");
        assert compareTablesDatabases(tablesDev, tables);
    }

    /**
     * ******************************************************************************************************************************************************************************************************************************
     */

    List<Attribute> getAttributeFromTable(Connection db, String table) {
        List<Attribute> attributes = new ArrayList<>();
        try {
            PreparedStatement pps = db.prepareStatement("desc " + table);
            ResultSet rs = pps.executeQuery();
            while (rs != null && rs.next()) {
                Attribute attribute = new Attribute();
                attribute.setField(rs.getString("Field"));
                attribute.setType(rs.getString("Type"));
                attribute.setCanNull(rs.getString("Null").equals("YES"));
                attribute.setKey(rs.getString("Key"));
                if (rs.getString("Default") != null) {
                    attribute.setDefaultValue(rs.getString("Default"));
                } else {
                    attribute.setDefaultValue("");
                }
                if (rs.getString("Extra") != null) {
                    attribute.setExtra(rs.getString("Extra"));
                } else {
                    attribute.setExtra("");
                }
                attributes.add(attribute);
            }
        } catch (Exception e) {
            System.err.println(e);
            assert e.toString().equals("");
        }
        return attributes;
    }

    boolean comparingAttributes(List<Attribute> attributesA, List<Attribute> attributesB, String table) {
        if (attributesA.size() != attributesB.size()) {
            return false;
        }
        for (int i = 0; i < attributesA.size(); i++) {
            if (!(attributesA.get(i).getField().equals(attributesB.get(i).getField()))) return false;
            Attribute attrA = attributesA.get(i);
            Attribute attrB = attributesB.get(i);
            if (!attrA.getType().equals(attrB.getType())) {
                System.err.println(attrA.getType() + " vs " + attrB.getType() + " - In :" + attrA.getField() + " - Of: " + table);
                return false;
            }
            if (attrA.isCanNull() != attrB.isCanNull()) {
                System.err.println(attrA.isCanNull() + " vs " + attrB.isCanNull() + " - In :" + attrA.getField() + " - Of: " + table);
                return false;
            }
            if (!attrA.getKey().equals(attrB.getKey())) {
                System.err.println(attrA.getKey() + " vs " + attrB.getKey() + " - In :" + attrA.getField() + " - Of: " + table);
                return false;
            }
            if (!attrA.getDefaultValue().equals(attrB.getDefaultValue())) {
                System.err.println(attrA.getDefaultValue() + " vs " + attrB.getDefaultValue() + " - In :" + attrA.getField() + " - Of: " + table);
                return false;
            }
            if (!attrA.getExtra().equals(attrB.getExtra())) {
                System.err.println(attrA.getExtra() + " vs " + attrB.getExtra() + " - In :" + attrA.getField() + " - Of: " + table);
                return false;
            }
        }
        return true;
    }

    boolean compareAttributesInTables(Connection dbA, Connection dbB, List<String> tables) {
        boolean result;
        for (int i = 0; i < tables.size(); i++) {
            List<Attribute> attributesA = getAttributeFromTable(dbA, tables.get(i));
            List<Attribute> attributesB = getAttributeFromTable(dbB, tables.get(i));
            result = comparingAttributes(attributesA, attributesB, tables.get(i));
            if (!result) return false;
        }
        return true;
    }

    @Test
    @Order(3)
    void compareAttributes() throws Exception {
        compareTables();
        System.out.println("PROD vs DEV");
        assert compareAttributesInTables(prod, dev, tables);
        System.out.println("DEV vs FILES");
        assert compareAttributesInTables(dev, files, tables);
    }

    /**
     * ******************************************************************************************************************************************************************************************************************************
     */

    List<Index> getIndexFromTable(Connection db, String table) {
        List<Index> indices = new ArrayList<>();
        try {
            PreparedStatement pps = db.prepareStatement("SHOW INDEX FROM " + table + " FROM " + ((ConnectionImpl) (db)).getDatabase());
            ResultSet rs = pps.executeQuery();
            while (rs != null && rs.next()) {
                Index index = new Index();
                index.setTable(table);
                index.setNoUnique(rs.getString("Non_unique").equals("1"));
                index.setKeyName(rs.getString("Key_name"));
                index.setColumnName(rs.getString("Column_name"));
                index.setCollation(rs.getString("Collation"));
                if (rs.getString("Sub_part") != null) {
                    index.setSubPart(rs.getString("Sub_part"));
                } else {
                    index.setSubPart("");
                }
                if (rs.getString("Packed") != null) {
                    index.setPacked(rs.getString("Packet"));
                } else {
                    index.setPacked("");
                }
                index.setCanNull(rs.getString("Null").equals("YES"));
                index.setIndexType(rs.getString("Index_type"));
                if (rs.getString("Comment") != null) {
                    index.setComment(rs.getString("Comment"));
                } else {
                    index.setComment("");
                }
                if (rs.getString("Index_comment") != null) {
                    index.setIndexComment(rs.getString("Index_comment"));
                } else {
                    index.setIndexComment("");
                }
                indices.add(index);
            }
        } catch (SQLException e) {
            System.err.println(e);
            assert e.toString().equals("");
        }
        return indices;
    }

    Index searchIndexInList(String field, List<Index> indices) {
        Index index = null;
        for (Index indexAux : indices) {
            String key = indexAux.getKeyName() + indexAux.getColumnName();
            if (key.equals(field)) {
                index = indexAux;
                break;
            }
        }
        return index;
    }

    boolean compareIndexesInTable(List<Index> indicesA, List<Index> indicesB, String table) {
        if (indicesA.size() != indicesB.size()) return false;
        for (Index indexA : indicesA) {
            Index indexB = searchIndexInList(indexA.getKeyName() + indexA.getColumnName(), indicesB);
            if (indexB == null) {
                System.err.println("Index " + indexA.getKeyName() + " in" + indexA.getTable() + " not found in indexB");
                return false;
            }
            if (!indexA.getTable().equals(indexB.getTable())) {
                System.err.println("Table " + indexA.getTable() + " vs " + indexB.getTable() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!(indexA.isNoUnique() == indexB.isNoUnique())) {
                System.err.println("Non unique " + indexA.isNoUnique() + " vs " + indexB.isNoUnique() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getKeyName().equals(indexB.getKeyName())) {
                System.err.println("Index name " + indexA.getKeyName() + " vs " + indexB.getKeyName() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getColumnName().equals(indexB.getColumnName())) {
                System.err.println("Column name " + indexA.getColumnName() + " vs " + indexB.getColumnName() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getCollation().equals(indexB.getCollation())) {
                System.err.println("Collation " + indexA.getCollation() + " vs " + indexB.getCollation() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getSubPart().equals(indexB.getSubPart())) {
                System.err.println("SubPart " + indexA.getSubPart() + " vs " + indexB.getSubPart() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getPacked().equals(indexB.getPacked())) {
                System.err.println("Packed " + indexA.getPacked() + " vs " + indexB.getPacked() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getIndexType().equals(indexB.getIndexType())) {
                System.err.println("IndexType " + indexA.getIndexType() + " vs " + indexB.getIndexType() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getComment().equals(indexB.getComment())) {
                System.err.println("Comment " + indexA.getComment() + " vs " + indexB.getComment() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
            if (!indexA.getIndexComment().equals(indexB.getIndexComment())) {
                System.err.println("IndexComment " + indexA.getIndexComment() + " vs " + indexB.getIndexComment() + " in " + indexA.getKeyName() + " of " + table);
                return false;
            }
        }
        return true;
    }

    HashMap<String, Integer> getCounts(List<Index> indices) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (Index indexA : indices) {
            String key = indexA.getColumnName() + indexA.getIndexType();
            if (counts.get(key) != null) {
                int c = counts.get(key);
                counts.put(key, c++);
            } else {
                counts.put(key, 1);
            }
        }
        return counts;
    }

    boolean compareIndexesInDatabase(Connection dbA, Connection dbB, List<String> tables) {
        boolean result;
        for (String table : tables) {
            List<Index> indicesA = getIndexFromTable(dbA, table);
            List<Index> indicesB = getIndexFromTable(dbB, table);
            if (!indicesMinimal) {
                result = compareIndexesInTable(indicesA, indicesB, table);
                if (!result) return false;
            } else {
                HashMap<String, Integer> countA = getCounts(indicesA);
                HashMap<String, Integer> countB = getCounts(indicesB);
                for (Index indexA : indicesA) {
                    String key = indexA.getColumnName() + indexA.getIndexType();
                    if (countA.get(key).compareTo(countB.get(key)) != 0) return false;
                }
            }
        }
        return true;
    }

    @Test
    @Order(4)
    void compareIndexes() throws Exception {
        compareTables();
        System.out.println("PROD vs DEV");
        assert compareIndexesInDatabase(prod, dev, tables);
        System.out.println("DEV vs FILES");
        assert compareIndexesInDatabase(dev, files, tables);
    }
}
