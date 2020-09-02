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

Ext.define('BagDatabase.views.ScriptResultGrid', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.scriptResultGrid',
    requires: ['BagDatabase.models.ScriptResult',
               'BagDatabase.stores.ScriptStore'],
    title: 'Script Results',
    columns: [{
        text: 'Run UUID', dataIndex: 'runUuid', flex: 2
    }, {
        text: 'Script ID', dataIndex: 'scriptId', flex: 1
    }, {
        text: 'Start Time', dataIndex: 'startTime', renderer: bagGridDateRenderer, flex: 1
    }, {
        text: 'Duration (s)', dataIndex: 'durationSecs', flex: 1
    }, {
        text: 'Std Output', dataIndex: 'stdout', flex: 2
    }, {
        text: 'Std Error', dataIndex: 'stderr', flex: 2
    }, {
        text: 'Error Message', dataIndex: 'errorMessage', flex: 2
    }, {
        text: 'Success', dataIndex: 'success', flex: 1
    }],
    listeners: {
        rowcontextmenu: function(tableview, record, tr, rowIndex, event) {
            var showTextInWindowFn = tableview.up('grid').showTextInWindow;
            Ext.create('Ext.menu.Menu', {
                items: [{
                    text: 'View Std Output',
                    iconCls: 'script-code-icon',
                    handler: function() {
                        showTextInWindowFn('Output for ' + record.get('runUuid'), record.get('stdout'));
                    }
                }, {
                    text: 'View Std Error',
                    iconCls: 'script-code-red-icon',
                    handler: function() {
                        showTextInWindowFn('Error for ' + record.get('runUuid'), record.get('stderr'));
                    }
                }]
            }).showAt(event.getXY());
            event.preventDefault();
        },
        rowdblclick: function(tableview, record) {
            tableview.up('grid').showTextInWindow('Output for ' + record.get('runUuid'), record.get('stdout'));
        }
    },
    header: {
        padding: 6,
        items: [{
           xtype: 'button',
           text: 'Refresh',
           iconCls: 'refresh-icon',
           itemId: 'refreshButton',
           handler: function(button) {
               button.up('grid').store.reload();
           }
        }]
    },
     initComponent: function() {
         var me = this;
         Ext.apply(this, {
             store: Ext.create('BagDatabase.stores.ScriptResultStore', {
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
     },
     showTextInWindow: function(title, text) {
         var win;
         try {
            text = JSON.stringify(JSON.parse(text), null, 2);
         }
         catch (err) {
            // Try to pretty-print the text if we can, no big deal if we can't
         }
         win = Ext.create('Ext.window.Window', {
             title: title,
             layout: 'fit',
             height: 500,
             width: 400,
             items: [{
                 xtype: 'form',
                 bodyPadding: 5,
                 layout: {
                     type: 'vbox',
                     align: 'stretch'
                 },
                 items: [{
                     xtype: 'textarea',
                     labelWidth: 0,
                     value: text,
                     fieldStyle: 'font-family: monospace;',
                     flex: 1
                 }]
             }]
         });
         win.show();
     }
});