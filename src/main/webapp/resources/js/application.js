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

Ext.application({
    name: 'Bag Database',
    requires: [ 'BagDatabase.views.BagGrid',
                'BagDatabase.views.BagTreePanel',
                'BagDatabase.views.NavigationButton',
                'BagDatabase.views.MapWindow',
                'BagDatabase.views.SearchPanel',
                'BagDatabase.views.BagTreeFilterPanel' ],
    launch: function() {
        Ext.create('Ext.container.Viewport', {
            layout: 'fit',
            id: 'viewport',
            items: [{
                xtype: 'tabpanel',
                region: 'center',
                activeTab: 0,
                items: [{
                    xtype: 'panel',
                    layout: 'border',
                    title: 'List View',
                    iconCls: 'table-icon',
                    items: [{
                        xtype: 'searchPanel',
                        region: 'north'
                    }, {
                        xtype: 'bagGrid',
                        itemId: 'bagGrid',
                        title: 'Search Results',
                        region: 'center'

                    }]
                }, {
                    xtype: 'panel',
                    layout: 'border',
                    title: 'Folder View',
                    iconCls: 'folder-icon',
                    items: [{
                        xtype: 'bagTreeFilterPanel',
                        region: 'north'
                    }, {
                        xtype: 'bagTreePanel',
                        itemId: 'bagTreePanel',
                        region: 'center'
                    }]
                }],
                tabBar: {
                    items: [{ xtype: 'tbfill' }, {
                        xtype: 'navigationButton',
                        iconCls: 'chart-organisation-icon',
                        isAdmin: isAdmin,
                        margin: 5
                    }]
                },
                fbar: [{
                    xtype: 'errorButton',
                    itemId: 'errorButton'
                }, '-', {
                    xtype: 'statusText',
                    itemId: 'statusText'
                }, '->'
                // TODO Re-enable the save button when cell editing is working
                /*, {
                    xtype: 'button',
                    text: 'Save Changes',
                    itemId: 'saveButton',
                    disabled: true,
                    handler: function(button) {
                        var grid = button.up('grid');
                        var store = grid.getStore();
                        var records = store.getRange().filter(function(bag) {
                            return bag.dirty;
                        });
                        var bags = [];
                        records.forEach(function(record) {
                            bags.push(record.data);
                        });

                        if (!grid.saveMask) {
                            grid.saveMask = new Ext.LoadMask(grid, {msg: 'Saving bags...'});
                        }
                        BagDatabase.stores.BagStore.saveBags(bags, grid.saveMask, button);
                        records.forEach(function(record) {
                            record.commit();
                        });
                    }
                }*/],
                listeners: {
                    afterrender: function(bagGrid) {
                        bagGrid.down('#statusText').connectWebSocket(bagGrid.down('#errorButton'));
                    }
                }
            }]
        });
    }
});
