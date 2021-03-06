/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.query.xcontent;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.OpenFilterClause;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.search.XBooleanFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.query.QueryParsingException;
import org.elasticsearch.index.settings.IndexSettings;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.common.collect.Lists.*;

/**
 * @author kimchy (shay.banon)
 */
public class BoolFilterParser extends AbstractIndexComponent implements XContentFilterParser {

    public static final String NAME = "bool";

    @Inject public BoolFilterParser(Index index, @IndexSettings Settings settings) {
        super(index, settings);
    }

    @Override public String[] names() {
        return new String[]{NAME};
    }

    @Override public Filter parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        List<OpenFilterClause> clauses = newArrayList();

        boolean cache = false;

        String filterName = null;
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if ("must".equals(currentFieldName)) {
                    clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.MUST));
                } else if ("must_not".equals(currentFieldName) || "mustNot".equals(currentFieldName)) {
                    clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.MUST_NOT));
                } else if ("should".equals(currentFieldName)) {
                    clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.SHOULD));
                }
            } else if (token == XContentParser.Token.START_ARRAY) {
                if ("must".equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.MUST));
                    }
                } else if ("must_not".equals(currentFieldName) || "mustNot".equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.MUST_NOT));
                    }
                } else if ("should".equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        clauses.add(new OpenFilterClause(parseContext.parseInnerFilter(), BooleanClause.Occur.SHOULD));
                    }
                }
            } else if (token.isValue()) {
                if ("_cache".equals(currentFieldName)) {
                    cache = parser.booleanValue();
                } else if ("_name".equals(currentFieldName)) {
                    filterName = parser.text();
                }
            }
        }

        XBooleanFilter boolFilter = new XBooleanFilter();
        for (OpenFilterClause filterClause : clauses) {
            boolFilter.add(filterClause);
        }
        Filter filter = boolFilter;
        if (cache) {
            filter = parseContext.cacheFilter(filter);
        }
        if (filterName != null) {
            parseContext.addNamedFilter(filterName, filter);
        }
        return filter;
    }
}