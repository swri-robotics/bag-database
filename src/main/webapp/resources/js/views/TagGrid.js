// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
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

Ext.define('BagDatabase.views.TagGrid', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.tagGrid',
    title: 'Tags',
    requires: ['BagDatabase.views.SetTagWindow'],
    store: {
        xtype: 'jsonstore',
        model: 'BagDatabase.models.Tag',
        sorters: 'tag',
        reader: {
            type: 'json'
        }
    },
    selModel: {
        mode: 'MULTI'
    },
    columns: [{
        text: 'Key', dataIndex: 'tag', flex: 1
    }, {
        text: 'Value: ', dataIndex: 'value', flex: 1
    }],
    buttons: [{
        text: 'Add',
        itemId: 'addButton',
        iconCls: 'tag-add-icon',
        disabled: false,
        handler: function(button) {
            var tagGrid, bagId, win;
            tagGrid = button.up('tagGrid');
            bagId = tagGrid.bagId;
            // Weird note: 'tagName' must be some kind of reserved word
            // somewhere in ExtJs, because trying to pass in a parameter
            // name that causes all kinds of weird issues.
            win = Ext.create('BagDatabase.views.SetTagWindow', {
                bagIds: [bagId],
                tagGrid: tagGrid
            });
            win.show();
        }
    }, {
        text: 'Remove',
        itemId: 'removeButton',
        iconCls: 'tag-delete-icon',
        disabled: false,
        handler: function(button) {
            var tagGrid, records, bagId;
            tagGrid = button.up('tagGrid');
            records = tagGrid.getSelection();
            bagId = tagGrid.bagId;

            if (records.length > 0) {
                Ext.Msg.confirm('Delete Confirmation',
                'Are you sure you want to delete ' + records.length + ' tag(s)?',
                function(buttonId) {
                    if (buttonId == 'yes') {
                        var tagNames = [];
                        records.forEach(function(record) {
                            tagNames.push(record.get('tag'));
                        });
                        Ext.Ajax.request({
                            url: 'bags/removeTags',
                            method: 'GET',
                            params:  {
                                tagNames: tagNames,
                                bagId: bagId
                            },
                            callback: function() {
                                tagGrid.up('window').reloadTags();
                            }
                        });
                    }
                }
                );
            }
        }
    }]
});