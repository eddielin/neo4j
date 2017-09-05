/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.impl.fulltext.integrations.bloom;

import org.apache.lucene.analysis.Analyzer;

import java.io.File;
import java.io.IOException;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.impl.fulltext.FulltextFactory;
import org.neo4j.kernel.api.impl.fulltext.FulltextProvider;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

class BloomKernelExtension extends LifecycleAdapter
{
    private final File storeDir;
    private final Config config;
    private final FileSystemAbstraction fileSystemAbstraction;
    private final GraphDatabaseService db;
    private final Procedures procedures;

    BloomKernelExtension( FileSystemAbstraction fileSystemAbstraction, File storeDir, Config config, GraphDatabaseService db, Procedures procedures )
    {
        this.storeDir = storeDir;
        this.config = config;
        this.fileSystemAbstraction = fileSystemAbstraction;
        this.db = db;
        this.procedures = procedures;
    }

    @Override
    public void init() throws IOException, ProcedureException
    {
        String[] properties = config.get( LoadableBloomFulltextConfig.bloom_indexed_properties ).toArray( new String[0] );
        Analyzer analyzer;
        try
        {
            Class configuredAnalayzer = Class.forName( config.get( LoadableBloomFulltextConfig.bloom_analyzer ) );
            analyzer = (Analyzer) configuredAnalayzer.newInstance();
        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Could not create the configured analyzer", e );
        }

        FulltextProvider provider = FulltextProvider.instance( db );
        FulltextFactory fulltextFactory = new FulltextFactory( fileSystemAbstraction, storeDir, analyzer );
        String bloomNodes = "bloomNodes";
        fulltextFactory.createFulltextIndex( bloomNodes, FulltextProvider.FULLTEXT_INDEX_TYPE.NODES, properties, provider );
        String bloomRelationships = "bloomRelationships";
        fulltextFactory.createFulltextIndex( bloomRelationships, FulltextProvider.FULLTEXT_INDEX_TYPE.RELATIONSHIPS, properties, provider );

        procedures.register( new BloomProcedure( FulltextProvider.FULLTEXT_INDEX_TYPE.NODES, bloomNodes, provider ) );
        procedures.register( new BloomProcedure( FulltextProvider.FULLTEXT_INDEX_TYPE.RELATIONSHIPS, bloomRelationships, provider ) );
    }

    @Override
    public void shutdown() throws Exception
    {
        FulltextProvider.instance( db ).close();
    }
}
