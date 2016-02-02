/*
 * Copyright 2016 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.frontend.find.core.comparison;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.autonomy.frontend.find.core.test.AbstractFindIT;
import com.hp.autonomy.searchcomponents.core.search.DocumentsService;
import com.hp.autonomy.searchcomponents.core.search.QueryRestrictions;
import com.hp.autonomy.searchcomponents.core.search.SearchResult;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.Serializable;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public abstract class AbstractComparisonServiceIT<S extends Serializable, R extends SearchResult, E extends Exception> extends AbstractFindIT {

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    private ComparisonService<R, E> comparisonService;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    protected DocumentsService<S, R, E> documentsService;

    protected ObjectMapper mapper = new ObjectMapper();

    protected String twoDocStateToken;
    protected String sixDocStateToken;
    protected String firstDiffStateToken;
    protected String secondDiffStateToken;

    protected abstract QueryRestrictions<S> buildQueryRestrictions();

    @Before
    public void createStateTokens() throws E {
        final QueryRestrictions<S> queryRestrictions = buildQueryRestrictions();

        twoDocStateToken = documentsService.getStateToken(queryRestrictions, 2);
        sixDocStateToken = documentsService.getStateToken(queryRestrictions, 6);

        // Comparison service is the easiest way to generate the diff state tokens without having to re-implement comparison logic
        final Comparison<R> comparison = comparisonService.compareStateTokens(twoDocStateToken, sixDocStateToken, 1, Integer.MAX_VALUE, "context", null, false);
        firstDiffStateToken = comparison.getDocumentsInFirstStateToken();
        secondDiffStateToken = comparison.getDocumentsInSecondStateToken();
    }

    @Test
    public void compareQueryStateTokens() throws Exception {
        final ComparisonRequest<S> comparisonRequest = new ComparisonRequest.Builder<S>()
                .setFirstQueryToken(twoDocStateToken)
                .setSecondQueryToken(sixDocStateToken)
                .build();

        mockMvc.perform(post(ComparisonController.PATH + '/')
                .content(mapper.writeValueAsString(comparisonRequest))
                .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                    .andExpect(jsonPath("$.documentsInBoth.documents", hasSize(2)))
                    .andExpect(jsonPath("$.documentsInFirst.documents", empty()))
                    .andExpect(jsonPath("$.documentsInSecond.documents", hasSize(4)));
    }

    @Test
    public void compareDiffStateTokens() throws Exception {
        final ComparisonRequest<S> comparisonRequest = new ComparisonRequest.Builder<S>()
                .setFirstQueryToken(twoDocStateToken)
                .setSecondQueryToken(sixDocStateToken)
                .setFirstDifferenceStateToken(firstDiffStateToken)
                .setSecondDifferenceStateToken(secondDiffStateToken)
                .build();

        mockMvc.perform(post(ComparisonController.PATH + '/')
                .content(mapper.writeValueAsString(comparisonRequest))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.documentsInBoth.documents", hasSize(2)))
                .andExpect(jsonPath("$.documentsInFirst.documents", empty()))
                .andExpect(jsonPath("$.documentsInSecond.documents", hasSize(4)));
    }

    @Test
    public void compareRestrictionsAndToken() throws Exception {
        final ComparisonRequest<S> comparisonRequest = new ComparisonRequest.Builder<S>()
                .setFirstRestrictions(buildQueryRestrictions())
                .setSecondQueryToken(sixDocStateToken)
                .build();

        mockMvc.perform(post(ComparisonController.PATH + '/')
                .content(mapper.writeValueAsString(comparisonRequest))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.documentsInBoth.documents", hasSize(6)))
                .andExpect(jsonPath("$.documentsInFirst.documents", not(empty())))
                .andExpect(jsonPath("$.documentsInSecond.documents", empty()));
    }

    @Test
    public void compareTokenAndRestrictions() throws Exception {
        final ComparisonRequest<S> comparisonRequest = new ComparisonRequest.Builder<S>()
                .setFirstQueryToken(twoDocStateToken)
                .setSecondRestrictions(buildQueryRestrictions())
                .build();

        mockMvc.perform(post(ComparisonController.PATH + '/')
                .content(mapper.writeValueAsString(comparisonRequest))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.documentsInBoth.documents", hasSize(2)))
                .andExpect(jsonPath("$.documentsInFirst.documents", empty()))
                .andExpect(jsonPath("$.documentsInSecond.documents", not(empty())));
    }

    @Test
    public void compareRestrictions() throws Exception {
        final ComparisonRequest<S> comparisonRequest = new ComparisonRequest.Builder<S>()
                .setFirstRestrictions(buildQueryRestrictions())
                .setSecondRestrictions(buildQueryRestrictions())
                .build();

        mockMvc.perform(post(ComparisonController.PATH + '/')
                .content(mapper.writeValueAsString(comparisonRequest))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.documentsInBoth.documents", not(empty())))
                .andExpect(jsonPath("$.documentsInFirst.documents", empty()))
                .andExpect(jsonPath("$.documentsInSecond.documents", empty()));
    }
}