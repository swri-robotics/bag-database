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

Ext.define('BagDatabase.views.ScriptGrid', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.scriptGrid',
    requires: ['BagDatabase.models.Script',
               'BagDatabase.stores.ScriptStore',
               'BagDatabase.views.ScriptWindow'],
    columns: [{
        text: 'Name', dataIndex: 'name', flex: 1
    }, {
        text: 'Description', dataIndex: 'description', flex: 2
    }, {
        text: 'Docker Image', dataIndex: 'dockerImage', flex: 1
    }, {
        text: 'Network Access?', dataIndex: 'allowNetworkAccess', flex: 1
    }, {
        text: 'Memory Limit (B)', dataIndex: 'memoryLimitBytes', flex: 1
    }, {
        text: 'Run Automatically?', dataIndex: 'runAutomatically', flex: 1
    }, {
        text: 'Timeout (s)', dataIndex: 'timeoutSecs', flex: 1
    }, {
        text: 'Created On', dataIndex: 'createdOn', renderer: bagGridDateRenderer, flex: 1
    }, {
        text: 'Updated On', dataIndex: 'updatedOn', renderer: bagGridDateRenderer, flex: 1
    }],
    listeners: {
        rowdblclick: function(grid, record) {
            var scriptId = record.get('id');
            grid.ownerCt.showScriptDetails(scriptId, grid.store);
        }
    },
    tbar: [{
        xtype: 'button',
        text: 'Create',
        iconCls: 'script-add-icon',
        itemId: 'createButton',
        handler: function(button) {
            var win, store;
            win = Ext.create('BagDatabase.views.ScriptWindow', {
                store: button.up('grid').store
            });
            win.show();
        }
    }, {
        xtype: 'button',
        text: 'Run',
        iconCls: 'script-go-icon',
        itemId: 'runButton'
    }, {
        xtype: 'button',
        text: 'Delete',
        iconCls: 'script-delete-icon',
        itemId: 'deleteButton',
        handler: function(button) {
            var selection, item, store;
            selection = button.up('grid').getSelection();
            if (selection && selection.length > 0) {
                item = selection[0];
                store = button.up('grid').store;
                Ext.Msg.confirm('Delete Script?',
                                'Are you sure you want to delete the script "' + item.get('name') + '"?',
                                function(buttonId) {
                                    if (buttonId == 'yes') {
                                        params = {
                                            scriptId: selection[0].get('id')
                                        };
                                        params[csrfName] = csrfToken;
                                        Ext.Ajax.request({
                                            url: 'scripts/delete',
                                            params: params,
                                            success: function() {
                                                store.reload();
                                            }
                                        });
                                    }
                                });
            }
        }
    }, {
        xtype: 'button',
        text: 'Refresh',
        iconCls: 'refresh-icon',
        itemId: 'refreshButton',
        handler: function(button) {
            button.up('grid').store.reload();
        }
    }],
    showScriptDetails: function(scriptId, store) {
        var win = Ext.create('BagDatabase.views.ScriptWindow', {
            scriptId: scriptId,
            store: store
        });
        win.show();
    },
    initComponent: function() {
        var me = this;
        Ext.apply(this, {
            store: Ext.create('BagDatabase.stores.ScriptStore', {
                listeners: {
                    beforeload: function(store) {
                        me.setLoading(true);
                    },
                    load: function(store) {
                        me.setLoading(false);
                    }
                }
            })
        });

        this.callParent(arguments);
    }
});