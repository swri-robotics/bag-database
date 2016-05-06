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

Ext.define('BagDatabase.views.BagGrid', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.bagGrid',
    title: 'Bag List',
    layout: 'fit',
    saveMask: null,
    stompClient: null,
    requires: ['Ext.grid.plugin.BufferedRenderer',
               'BagDatabase.stores.BagStore',
               'BagDatabase.views.BagDetailsWindow',
               'BagDatabase.views.ErrorWindow',
               'BagDatabase.views.StatusText',
               'BagDatabase.views.ErrorButton'],
    listeners: {
        afterrender: function(bagGrid) {
            bagGrid.down('#statusText').connectWebSocket(bagGrid.down('#errorButton'));
        },
        edit: function(editor, context, event) {
            var origVal;
            if (context.record.modified && context.record.modified[context.field]){
                origVal = context.record.modified[context.field];
            }
            else {
                origVal = context.originalValue;
            }
            var newVal = context.value;
            if (!newVal) {
                newVal = null;
            }
            var valueChanged = false;

            if (typeof(newVal) == 'number' && typeof(origVal) == 'number') {
                if (Math.abs(Math.abs(newVal) - Math.abs(origVal)) > 0.0000000001) {
                    valueChanged = true;
                }
            }
            else if (newVal != origVal) {
                valueChanged = true;
            }

            if (valueChanged) {
                // If the value has actually changed, enable the save button.
                editor.grid.down('#saveButton').setDisabled(false);
                //context.record.commit();
            }
            else {
                context.record.reject();
            }
        },
        rowcontextmenu: function(grid, record, tr, rowIndex, event) {
            var records = grid.getSelection();
            var items;
            if (records.length == 1) {
                items = [{
                    text: 'View Bag Information',
                    iconCls: 'information-icon',
                    handler: function() {
                        grid.ownerCt.showBagDetails(record.get('id'));
                    }
                }, {
                    text: 'Display Bag on Map',
                    iconCls: 'map-icon',
                    handler: function() {
                        grid.ownerCt.displayBagsOnMap([record]);
                    }
                }, {
                    text: 'Download Bag',
                    iconCls: 'save-icon',
                    handler: function() {
                        grid.ownerCt.downloadBags([record]);
                    }
                }];
            }
            else {
                items = [{
                    text: 'Display Bags on Map',
                    iconCls: 'map-icon',
                    handler: function() {
                        grid.ownerCt.displayBagsOnMap(records);
                    }
                }, {
                    text: 'Download Bags',
                    iconCls: 'save-icon',
                    handler: function() {
                        grid.ownerCt.downloadBags(records);
                    }
                }];
            }
            Ext.create('Ext.menu.Menu', {
                items: items
            }).showAt(event.getXY());
            event.preventDefault();
        },
        rowdblclick: function(grid, record) {
            var bagId = record.get('id');
            grid.ownerCt.showBagDetails(bagId);
        }
    },
    selModel: {
        //selType: 'cellmodel',
        mode: 'MULTI',
        pruneRemoved: false
    },
    plugins: [
    /**
     * TODO Cell editing is disabled because it does not work right with the
     * BufferedStore, which is very important for performance.  Hopefully
     * ExtJS will fix the bug and then we can re-enable it.
     */
    /*{
        ptype: 'cellediting',
        clicksToEdit: 1
    }, */{
        ptype: 'gridfilters'
    }],
    columns: [{
        text: 'Id', dataIndex: 'id', hidden: true, flex: 1, filter: { type: 'number' }
    }, {
        text: 'Path', dataIndex: 'path', hidden: true, flex: 2, filter: { type: 'string' }
    }, {
        text: 'Filename', dataIndex: 'filename', flex: 2, filter: { type: 'string' }
    }, {
        text: 'Location', dataIndex: 'location', flex: 1, editor: 'textfield', filter: { type: 'string' }
    }, {
        text: 'Vehicle', dataIndex: 'vehicle', flex: 1, editor: 'textfield', filter: { type: 'string' }
    }, {
        text: 'Description', dataIndex: 'description', flex: 1, editor: 'textfield',
        hidden: true, filter: { type: 'string' }
    }, {
        text: 'Latitude (Deg)', dataIndex: 'latitudeDeg', flex: 1, editor: {
            xtype: 'numberfield', decimalPrecision: 10 },
        hidden: true, filter: { type: 'number' }
    }, {
        text: 'Longitude (Deg)', dataIndex: 'longitudeDeg', flex: 1, editor: {
            xtype: 'numberfield', decimalPrecision: 10 },
        hidden: true, filter: { type: 'number' }
    }, {
        text: 'Missing file?', dataIndex: 'missing', flex: 0.5,
        hidden: true, filter: { type: 'boolean' }
    }, {
        text: 'MD5 Sum', dataIndex: 'md5sum', flex: 1,
        hidden: true, filter: { type: 'string' }
    }, {
        text: 'Duration (s)', dataIndex: 'duration', flex: 1, filter: { type: 'number' },
        renderer: function(value) {
            if (value < 0) {
                return '(Invalid)';
            }
            return value.toFixed(3);
        }
    }, {
        text: 'Created On', dataIndex: 'createdOn', flex: 1,
        renderer: Ext.util.Format.dateRenderer('n/j/Y H:m:s'),
        hidden: true, filter: { type: 'date', dateFormat: 'time' }
    }, {
        text: 'Updated On', dataIndex: 'updatedOn', flex: 1,
        renderer: Ext.util.Format.dateRenderer('n/j/Y H:m:s'),
        hidden: true, filter: { type: 'date', dateFormat: 'time' }
    }, {
        text: 'Start Time', dataIndex: 'startTime', flex: 1, filter: { type: 'date', dateFormat: 'time' },
        renderer: Ext.util.Format.dateRenderer('n/j/Y H:m:s')
    }, {
        text: 'End Time', dataIndex: 'endTime', flex: 1, filter: { type: 'date', dateFormat: 'time' },
        renderer: Ext.util.Format.dateRenderer('n/j/Y H:m:s')
    }, {
        text: 'Size (MB)', dataIndex: 'size', flex: 1, filter: { type: 'number' },
        renderer: function(value) {
            return (value / 1024.0 / 1024.0).toFixed(3) ;
        },
        hidden: true
    }, {
        text: 'Messages', dataIndex: 'messageCount', flex: 1,
        hidden: true, filter: { type: 'number' }
    }, {
        xtype: 'actioncolumn',
        width: 75,
        items: [{
            tooltip: 'Bag Information',
            iconCls: 'bag-action-icon information-icon',
            handler: function(grid, rowIndex, colIndex) {
                grid.ownerCt.showBagDetails(grid.getStore().getAt(rowIndex).get('id'));
            }
        }, {
            iconCls: 'bag-action-icon map-icon',
            isDisabled: function(view, rowIndex, colIndex, item, record) {
                return record.get('hasPath') !== true;
            },
            getTip: function(value, metadata, record) {
                return record.get('hasPath') === true ? 'Display on Map' : 'No Path Available';
            },
            handler: function(grid, rowIndex, colIndex) {
                grid.ownerCt.displayBagsOnMap([grid.getStore().getAt(rowIndex)]);
            }
        }, {
            iconCls: 'save-icon',
            tooltip: 'Download',
            handler: function(grid, rowIndex, colIndex) {
                grid.ownerCt.downloadBags([grid.getStore().getAt(rowIndex)]);
            }
        }]
    }],
    viewConfig: {
        getRowClass: function(record, index, rowParams, store) {
            return record.get('missing') ? 'missing-bag-row' : '';
        }
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
    displayBagInfo: function(bagRecord) {
        var bagId = bagRecord.get('id');
        this.showBagDetails(bagId);
    },
    displayBagsOnMap: function(bagRecords) {
        var bagIds = [];
        var bagFilenames = [];
        bagRecords.forEach(function(record) {
            bagIds.push(record.get('id'));
            bagFilenames.push(record.get('filename'));
        });
        var win = Ext.create({
            xtype: 'mapWindow',
            title: 'Path for ' + bagFilenames[0],
            width: 600,
            height: 600
        });
        win.show();

        var loadMask = new Ext.LoadMask({
            target: win,
            msg: 'Loading coordinates...'
        });
        loadMask.show();

        var params = {
            bagIds: bagIds
        };
        params[csrfName] = csrfToken;
        Ext.Ajax.request({
            url: 'bags/coords',
            params: params,
            timeout: 60000,
            callback: function(options, success, response) {
                loadMask.hide();
                if (!success) {
                    console.log('Error retrieving bag.');
                    return;
                }

                var coords = Ext.util.JSON.decode(response.responseText);
                win.addRoute(coords);
            }
        });
    },
    downloadBags: function(bagRecords) {
        var files = [];
        bagRecords.forEach(function(record) {
            files.push({
                filename: record.get('filename'),
                download: 'bags/download?bagId=' + record.get('id')
            });
        });

        function download_next(i) {
            if(i >= files.length) {
                return;
            }
            var a = document.createElement('a');
            a.href = files[i].download;
            a.target = '_parent';
            // Use a.download if available, it prevents plugins from opening.
            if ('download' in a) {
                a.download = files[i].filename;
            }
            // Add a to the doc for click to work.
            (document.body || document.documentElement).appendChild(a);
            if (a.click) {
                a.click(); // The click method is supported by most browsers.
            } else {
                $(a).click(); // Backup using jquery
            }
            // Delete the temporary link.
            a.parentNode.removeChild(a);
            // Download the next file with a small timeout. The timeout is necessary
            // for IE, which will otherwise only download the first file.
            setTimeout(function () { download_next(i + 1); }, 500);
        }
        // Initiate the first download.
        download_next(0);
    },
    showBagDetails: function(bagId) {
        var win = Ext.create({
            xtype: 'bagDetailsWindow',
            bagId: bagId
        });
        win.show();
    },
    initComponent: function() {
        var me = this;
        Ext.apply(this, {
            store: Ext.create('BagDatabase.stores.BagStore', {
                csrfName: csrfName,
                csrfToken: csrfToken,
                listeners: {
                    beforeload: function(store) {
                        me.setLoading(true);
                    },
                    load: function(store) {
                        me.setLoading(false);
                        me.setTitle ('Bag List (' + store.getCount() + ' bags)');
                    }
                }
            })
        });

        this.callParent(arguments);
    }
});