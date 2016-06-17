/*
 * Copyright (c) 2013-2016 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.graphaware.module.es.mapping;

import com.graphaware.common.representation.NodeRepresentation;
import com.graphaware.writer.thirdparty.NodeCreated;
import com.graphaware.writer.thirdparty.NodeDeleted;
import com.graphaware.writer.thirdparty.NodeUpdated;
import io.searchbox.action.BulkableAction;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This mapping indexes all documents in the same ElasticSearch index.
 *
 * The node's neo4j labels are stored are ElasticSearch "type".
 * If a node has multiple labels, it is stored multiple times, once for each label.
 *
 * Relationships are not indexed.
 */
public class DefaultMapping extends Mapping {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultMapping.class);

    public DefaultMapping(String index, String keyProperty) {
        super(index, keyProperty);
    }

    @Override
    public Map<String, String> map(NodeRepresentation node) {
        Map<String, String> source = new LinkedHashMap<>();
        for (String key : node.getProperties().keySet()) {
            source.put(key, String.valueOf(node.getProperties().get(key)));
        }
        return source;
    }

    @Override
    protected List<BulkableAction<? extends JestResult>> deleteNode(NodeDeleted operation) {
        NodeRepresentation node = operation.getDetails();
        String id = getKey(node);
        List<BulkableAction<? extends JestResult>> actions = new ArrayList<>();

        for (String label : node.getLabels()) {
            actions.add(new Delete.Builder(id).index(getIndex()).type(label).build());
        }

        return actions;
    }

    @Override
    protected List<BulkableAction<? extends JestResult>> updateNode(NodeUpdated operation) {
        return createOrUpdateNode(operation.getDetails().getCurrent());
    }

    @Override
    protected List<BulkableAction<? extends JestResult>> createNode(NodeCreated operation) {
        return createOrUpdateNode(operation.getDetails());
    }

    private List<BulkableAction<? extends JestResult>> createOrUpdateNode(NodeRepresentation node) {
        String id = getKey(node);
        Map<String, String> source = map(node);
        List<BulkableAction<? extends JestResult>> actions = new ArrayList<>();

        for (String label : node.getLabels()) {
            actions.add(new Index.Builder(source).index(getIndex()).type(label).id(id).build());
        }

        return actions;
    }

    @Override
    public void createIndexAndMapping(JestClient client, String index) throws Exception {
        if (client.execute(new IndicesExists.Builder(index).build()).isSucceeded()) {
            LOG.info("Index " + index + " already exists in ElasticSearch.");
        }

        LOG.info("Index " + index + " does not exist in ElasticSearch, creating...");

        final JestResult execute = client.execute(new CreateIndex.Builder(index).build());

        if (execute.isSucceeded()) {
            LOG.info("Created ElasticSearch index.");
        } else {
            LOG.error("Failed to create ElasticSearch index. Details: " + execute.getErrorMessage());
        }
    }

}