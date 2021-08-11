// *****************************************************************************
//
// Copyright (c) 2016, Southwest Research Institute® (SwRI®)
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

// Sorting entries in the tree using this type will ensure that directories
// are listed before files.
Ext.apply(Ext.data.SortTypes, {
    asFilename: function(filename) {
        if (!filename) {
            return '';
        }

        if (filename.endsWith('.bag')) {
            return 'z' + filename.toLowerCase();
        }

        return 'a' + filename.toLowerCase();
    }
});

Ext.define('BagDatabase.models.BagTreeNode', {
    extend: 'Ext.data.TreeModel',
    requires: ['BagDatabase.models.Base',
               'BagDatabase.models.MessageType',
               'BagDatabase.models.Tag',
               'BagDatabase.models.Topic'],
    idProperty: 'id',
    fields: [{
        // This will be the full path to the bag or directory on disk;
        // paths should be unique.
        name: 'id'
    }, {
        // For bag files, this will contain the actual Bag object.  For
        // directories, this will be null.
        name: 'bag', type: 'auto'
    }, {
        // For directories, this will contain the total number of bag files
        // under the directory and all of its subdirectories.  For bag files,
        // this will be -1.
        name :'bagCount', type: 'int'
    }, {
        // If the tree of bags is filtered, this will contain how many bags
        // match the filter under the current branch.
        name: 'filteredBagCount', type: 'int'
    }, {
        name: 'filename', sortType: 'asFilename'
    }, {
        name: 'bagId', mapping: 'bag.id', type: 'int'
    }, {
        name: 'compressed', mapping: 'bag.compressed', type: 'boolean'
    }, {
        name: 'createdOn', mapping: 'bag.createdOn', type: 'date', dateFormat: 'time'
    }, {
        name: 'duration', mapping: 'bag.duration', type: 'float'
    }, {
        name: 'endTime', mapping: 'bag.endTime', type: 'date', dateFormat: 'time'
    }, {
        name: 'hasPath', mapping: 'bag.hasPath', type: 'boolean'
    }, {
        name: 'indexed', mapping: 'bag.indexed', type: 'boolean'
    }, {
        name: 'latitudeDeg', mapping: 'bag.latitudeDeg', type: 'float'
    }, {
        name: 'location', mapping: 'bag.location', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'longitudeDeg', mapping: 'bag.longitudeDeg', type: 'float'
    }, {
        name: 'md5sum', mapping: 'bag.md5sum', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'messageCount', mapping: 'bag.messageCount', type: 'int'
    }, {
        name: 'missing', mapping: 'bag.missing', type: 'boolean'
    }, {
        name: 'path', mapping: 'bag.path', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'size', mapping: 'bag.size', type: 'float'
    }, {
        name: 'startTime', mapping: 'bag.startTime', type: 'date', dateFormat: 'time'
    }, {
        name: 'storageId', mapping: 'bag.storageId'
    }, {
        name: 'updatedOn', mapping: 'bag.updatedOn', type: 'date', dateFormat: 'time'
    }, {
        name: 'vehicle', mapping: 'bag.vehicle', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }, {
        name: 'version', mapping: 'bag.version', sortType: function(value) {
            if (!value) {
                return '';
            }
            return value;
        }
    }]
});