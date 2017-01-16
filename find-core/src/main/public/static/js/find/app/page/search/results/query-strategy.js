/*
 * Copyright 2016 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

define(['underscore'], function(_) {
    'use strict';

    return {
        requestParams: function(queryModel) {
            return {
                indexes: queryModel.get('indexes'),
                field_text: queryModel.get('fieldText'),
                min_date: queryModel.getIsoDate('minDate'),
                max_date: queryModel.getIsoDate('maxDate'),
                summary: 'context',
                min_score: '80',
                max_score: '100',
                text: queryModel.get('queryText')
            };
        },

        validateQuery: function(queryModel) {
            return Boolean(queryModel.get('queryText'));
        },

        waitForIndexes: function(queryModel) {
            return _.isEmpty(queryModel.get('indexes'));
        }
    };

});
