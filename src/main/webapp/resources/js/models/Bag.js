// *****************************************************************************
//
// Copyright (c) 2020, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

Ext.define('BagDatabase.models.Bag', {
    extend: 'BagDatabase.models.Base',
    requires: ['BagDatabase.models.Base',
               'BagDatabase.models.MessageType',
               'BagDatabase.models.Tag',
               'BagDatabase.models.Topic'],
    idProperty: 'id',
    fields: [{
        name: 'id', type: 'int'
    }, {
        name: 'compressed', type: 'boolean'
    }, {
        name: 'createdOn', type: 'date', dateFormat: 'time'
    }, {
        name: 'duration', type: 'float'
    }, {
        name: 'endTime', type: 'date', dateFormat: 'time'
    }, {
        name: 'filename', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'hasPath', type: 'boolean'
    }, {
        name: 'indexed', type: 'boolean'
    }, {
        name: 'latitudeDeg', type: 'float'
    }, {
        name: 'location', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'longitudeDeg', type: 'float'
    }, {
        name: 'md5sum', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'messageCount', type: 'int'
    }, {
        name: 'missing', type: 'boolean'
    }, {
        name: 'path', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'size', type: 'float'
    }, {
        name: 'startTime', type: 'date', dateFormat: 'time'
    }, {
        name: 'storageId'
    }, {
        name: 'updatedOn', type: 'date', dateFormat: 'time'
    }, {
        name: 'vehicle', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'version', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }]
});