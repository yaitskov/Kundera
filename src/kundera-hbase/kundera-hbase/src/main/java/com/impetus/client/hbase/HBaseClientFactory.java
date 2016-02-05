/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.client.hbase;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTablePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.impetus.client.hbase.config.HBasePropertyReader;
import com.impetus.client.hbase.schemamanager.HBaseSchemaManager;
import com.impetus.kundera.PersistenceProperties;
import com.impetus.kundera.client.Client;
import com.impetus.kundera.configure.ClientProperties.DataStore.Connection;
import com.impetus.kundera.configure.schema.api.SchemaManager;
import com.impetus.kundera.loader.GenericClientFactory;
import com.impetus.kundera.metadata.KunderaMetadataManager;
import com.impetus.kundera.metadata.model.PersistenceUnitMetadata;

/**
 * HBaseClientFactory, instantiates client for HBase
 */
public class HBaseClientFactory extends GenericClientFactory
{

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(HBaseClientFactory.class);

    /** The conf. */
    private Configuration conf;

    private static final String DEFAULT_ZOOKEEPER_PORT = "2181";

    @Override
    public void initialize(Map<String, Object> externalProperty)
    {
        setExternalProperties(externalProperty);
        initializePropertyReader();
        // Initialize HBase configuration
        PersistenceUnitMetadata puMetadata = KunderaMetadataManager.getPersistenceUnitMetadata(kunderaMetadata, getPersistenceUnit());

        String node = null;
        String port = null;
        if (externalProperty != null)
        {
            node = (String) externalProperty.get(PersistenceProperties.KUNDERA_NODES);
            port = (String) externalProperty.get(PersistenceProperties.KUNDERA_PORT);
        }
        if (node == null)
        {
            node = puMetadata.getProperties().getProperty(PersistenceProperties.KUNDERA_NODES);
        }
        if (port == null)
        {
            port = puMetadata.getProperties().getProperty(PersistenceProperties.KUNDERA_PORT);
        }

        onValidation(node, port);

        Configuration hadoopConf = new Configuration();
        hadoopConf.set("hbase.master", node + ":" + port);

        Connection conn = HBasePropertyReader.hsmd.getDataStore() != null ? HBasePropertyReader.hsmd.getDataStore()
                .getConnection() : null;
        if (conn != null && conn.getProperties() != null)
        {
            String zookeeperHost = conn.getProperties().getProperty("hbase.zookeeper.quorum").trim();
            String zookeeperPort = conn.getProperties().getProperty("hbase.zookeeper.property.clientPort").trim();
            hadoopConf.set("hbase.zookeeper.quorum", zookeeperHost != null ? zookeeperHost : node);
            hadoopConf.set("hbase.zookeeper.property.clientPort", zookeeperPort != null ? zookeeperPort
                    : DEFAULT_ZOOKEEPER_PORT);
        }
        else
        {
            // in case "hbase.zookeeper.property.clientPort" is not supplied, it
            // is different than hbase master port!
            hadoopConf.set("hbase.zookeeper.quorum", node);
            hadoopConf.set("hbase.zookeeper.property.clientPort", DEFAULT_ZOOKEEPER_PORT);
        }
        conf = HBaseConfiguration.create(hadoopConf);
        reader = new HBaseEntityReader(kunderaMetadata);
    }

    @Override
    protected Object createPoolOrConnection()
    {
        return null;
    }

    @Override
    protected Client instantiateClient(String persistenceUnit)
    {
        return new HBaseClient(indexManager, conf, reader, persistenceUnit, externalProperties, clientMetadata, kunderaMetadata);
    }

    @Override
    public boolean isThreadSafe()
    {
        return false;
    }

    @Override
    public void destroy()
    {
        // TODO destroy pool
        // hTablePool = null;

        // indexManager.close();
        if (schemaManager != null)
        {
            schemaManager.dropSchema();
        }
        externalProperties = null;
        schemaManager = null;
    }

    @Override
    public SchemaManager getSchemaManager(Map<String, Object> externalProperty)
    {
        setExternalProperties(externalProperty);
        if (schemaManager == null)
        {
            initializePropertyReader();
            schemaManager = new HBaseSchemaManager(HBaseClientFactory.class.getName(), externalProperty, kunderaMetadata);
        }
        return schemaManager;
    }

    /**
     * 
     */
    private void initializePropertyReader()
    {
        if (propertyReader == null)
        {
            propertyReader = new HBasePropertyReader(externalProperties, kunderaMetadata.getApplicationMetadata()
                    .getPersistenceUnitMetadata(getPersistenceUnit()));
            propertyReader.read(getPersistenceUnit());
        }
    }

    @Override
    protected void initializeLoadBalancer(String loadBalancingPolicyName)
    {
        throw new UnsupportedOperationException("Load balancing feature is not supported in "
                + this.getClass().getSimpleName());
    }
}
