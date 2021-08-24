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

Ext.define('BagDatabase.views.BagTreePanel', {
    extend: 'Ext.tree.Panel',
    alias: 'widget.bagTreePanel',
    title: 'Folder Structure',
    requires: [ 'BagDatabase.models.BagTreeNode',
                'BagDatabase.stores.BagTreeStore' ],
    reserveScrollbar: true,
    loadMask: true,
    useArrows: true,
    rootVisible: false,
    selModel: {
        mode: 'MULTI',
        pruneRemoved: false
    },
    header: {
        padding: 6,
        items: [{
            xtype: 'button',
            text: 'Info',
            itemId: 'infoButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'information-icon',
            handler: function(button) {
                var grid, records, treepanel;
                grid = button.up('viewport').down('bagGrid');
                treepanel = button.up('treepanel');
                records = treepanel.getSelection();
                grid.showBagDetails(records[0].get('bagId'));
            }
        }, {
            xtype: 'button',
            text: 'Add Tag',
            itemId: 'addTagButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'tag-add-icon',
            handler: function(button) {
                var grid, records, treepanel;
                grid = button.up('viewport').down('bagGrid');
                treepanel = button.up('treepanel');
                records = treepanel.getBagRecords(treepanel.getSelection());
                grid.addTag(records);
            }
        }, {
            xtype: 'button',
            text: 'Copy Link',
            itemId: 'copyLinkButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'link-icon',
            handler: function(button) {
                var grid, links, records, treepanel;
                grid = button.up('viewport').down('bagGrid');
                treepanel = button.up('treepanel');
                records = treepanel.getBagRecords(treepanel.getSelection());
                links = [];
                records.forEach(function(record) {
                    links.push(document.location.href +
                        'bags/download?bagId=' + record.get('id'));
                });
                grid.copyTextToClipboard(links.join('\n'));
            }
        }, {
            xtype: 'button',
            text: 'Map Bag',
            itemId: 'mapBagButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'map-icon',
            handler: function(button) {
                var grid, records, treepanel;
                grid = button.up('viewport').down('bagGrid');
                treepanel = button.up('treepanel');
                records = treepanel.getBagRecords(treepanel.getSelection());
                grid.displayBagsOnMap(records);
            }
        }, {
            xtype: 'button',
            text: 'Download Bag',
            itemId: 'downloadButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'save-icon',
            handler: function(button) {
                var grid, records, treepanel;
                grid = button.up('viewport').down('bagGrid');
                treepanel = button.up('treepanel');
                records = treepanel.getBagRecords(treepanel.getSelection());
                grid.downloadBags(records);
            }
        }, {
            xtype: 'splitbutton',
            text: 'Run Script',
            itemId: 'runScriptButton',
            margin: '0 0 0 5',
            disabled: true,
            iconCls: 'script-go-icon',
            listeners: {
                click: function(button, event) {
                    button.showMenu(event);
                },
                menushow: function(button, menu) {
                    var scriptStore, grid, records, bagIds, treepanel;
                    bagIds = [];
                    grid = button.up('viewport').down('bagGrid');
                    treepanel = button.up('treepanel');
                    records = treepanel.getBagRecords(treepanel.getSelection());
                    scriptStore = Ext.getStore('scriptStore');
                    scriptStore.reload();

                    records.forEach(function(record) {
                        bagIds.push(record.get('id'));
                    });
                    menu.removeAll();
                    scriptStore.each(function(record, idx) {
                        menu.add({
                            text: record.get('name'),
                            iconCls: 'script-icon',
                            handler: Ext.Function.pass(scriptStore.runScript, [record.get('id'), bagIds])
                        })
                    });
                }
            },
            menu: [{
                text: 'About', iconCls: 'information-icon', handler: function() {
                    var win = Ext.create('BagDatabase.views.AboutWindow');
                    win.show();
                }
            }, {
                text: 'API Documentation', iconCls: 'book-icon', handler: function() {
                    window.open("resources/docs/index.html", "_blank");
                }
            }]
        }, {
            xtype: 'button',
            text: 'Refresh',
            margin: '0 0 0 5',
            iconCls: 'refresh-icon',
            handler: function(button) {
                button.up('bagTreePanel').getStore().load();
            }
        }]
    },
    columns: [{
        xtype: 'treecolumn',
        text: 'File Name',
        sortable: true,
        dataIndex: 'filename',
        flex: 2,
        renderer: function(value, metadata, record) {
            var count, filteredCount;
            count = record.get('bagCount');
            if (count >= 0) {
                filteredCount = record.get('filteredBagCount');
                return value + ' <b>(' +
                    (filteredCount >= 0 ? filteredCount + '/' : '') +
                    count + ')</b>';
            }
            return value;
        }
    }, {
        text: 'Storage Id', dataIndex: 'storageId', flex: 1, hidden: true
    }, {
        text: 'Location', dataIndex: 'location', flex: 1
    }, {
        text: 'Vehicle', dataIndex: 'vehicle', flex: 1, editor: 'textfield'
    }, {
        text: 'Description', dataIndex: 'description', flex: 1, editor: 'textfield',
        hidden: true
    }, {
        text: 'Latitude (Deg)', dataIndex: 'latitudeDeg', flex: 1, editor: {
            xtype: 'numberfield', decimalPrecision: 10 },
        hidden: true,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            return value;
        }
    }, {
        text: 'Longitude (Deg)', dataIndex: 'longitudeDeg', flex: 1, editor: {
            xtype: 'numberfield', decimalPrecision: 10 },
        hidden: true,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            return value;
        }
    }, {
        text: 'Missing file?', dataIndex: 'missing', flex: 0.5,
        hidden: true,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            return value;
        }
    }, {
        text: 'MD5 Sum', dataIndex: 'md5sum', flex: 1,
        hidden: true
    }, {
        text: 'Duration (s)', dataIndex: 'duration', flex: 1,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            if (value < 0) {
                return '(Invalid)';
            }
            return value.toFixed(3);
        }
    }, {
        text: 'Created On', dataIndex: 'createdOn', flex: 1,
        renderer: bagGridDateRenderer,
        hidden: true
    }, {
        text: 'Updated On', dataIndex: 'updatedOn', flex: 1,
        renderer: bagGridDateRenderer,
        hidden: true
    }, {
        text: 'Start Time', dataIndex: 'startTime', flex: 1,
        renderer: bagGridDateRenderer
    }, {
        text: 'End Time', dataIndex: 'endTime', flex: 1,
        renderer: bagGridDateRenderer
    }, {
        text: 'Size (MB)', dataIndex: 'size', flex: 1,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            return (value / 1024.0 / 1024.0).toFixed(3) ;
        },
        hidden: true
    }, {
        text: 'Messages', dataIndex: 'messageCount', flex: 1,
        hidden: true,
        renderer: function(value, metadata, record) {
            if (!record.get('leaf')) {
                return '';
            }
            return value;
        }
    }, {
        text: 'Tags', dataIndex: 'tags', flex: 1.5, sortable: false,
        renderer: function(value, metadata, record) {
            var bag, strTags;
            bag = record.get('bag');
            if (!bag || !bag.tags) {
                return '';
            }

            strTags = [];
            bag.tags.forEach(function(tag) {
                var key, value;
                key = Ext.String.htmlEncode(tag['tag']);
                value = tag['value'];
                if (value) {
                    value = Ext.String.htmlEncode(value);
                }
                strTags.push(key + (value ? (': ' + value) : value));
            });

            strTags = strTags.sort(function (a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); });
            metadata.tdAttr = 'data-qtip="' + strTags.join("<br>") + '"';
            return strTags.join(', ');
        }
    }, {
        xtype: 'actioncolumn',
        width: 25,
        items: [{
            iconCls: 'bag-action-icon map-icon',
            isDisabled: function(view, rowIndex, colIndex, item, record) {
                return !record.get('leaf') || record.get('hasPath') !== true;
            },
            getTip: function(value, metadata, record) {
                return record.get('hasPath') === true ? 'Display on Map' : 'No Path Available';
            },
            handler: function(grid, rowIndex, colIndex) {
                var bag = grid.ownerCt.getBagRecords(
                    grid.getStore().getAt(rowIndex));
                grid.up('viewport').down('bagGrid').displayBagsOnMap(bag);
            }
        }]
    }],
    updateFilteredBagCounts: function() {
        var filterText = this.up('panel').down('#filterText').getValue();

        if (!filterText || filterText.length == 0) {
            // If there's no filter set, don't set a filtered count.
            this.getStore().each(function(record) {
                record.set('filteredBagCount', -1);
            });
            return;
        }

        Ext.Ajax.request({
            url: 'bags/filteredcount',
            method: 'GET',
            params: {
                text: filterText
            },
            callback: function(opts, success, response) {
                var results = Ext.decode(response.responseText);
                results.forEach(function(bagCount) {
                    var dir = this.getStore().getById(bagCount.path.replace(/\/$/, ""));
                    if (dir) {
                        dir.set('filteredBagCount', bagCount.count);
                    }
                }.bind(this));
            }.bind(this)
        });
    },
    getBagRecords: function(treeNodes) {
        var bags = [];
        if (!treeNodes.length) {
            treeNodes = [treeNodes];
        }
        treeNodes.forEach(function(node) {
            if (node.get('bag')) {
                bags.push(Ext.create('BagDatabase.models.Bag', node.get('bag')));
            }
        });
        return bags;
    },
    listeners: {
        rowcontextmenu: function(rowmodel, record, tr, rowIndex, event) {
            var records, items, bagIds, bagGrid, pluralSuffix, scriptStore, treepanel, scriptItems;
            treepanel = rowmodel.up('treepanel');
            records = treepanel.getBagRecords(treepanel.getSelection());
            items = [];
            pluralSuffix = records.length == 1 ? '' : 's';
            scriptStore = Ext.getStore('scriptStore');
            if (!records || records.length == 0) {
                return;
            }
            bagGrid = treepanel.up('viewport').down('bagGrid');
            if (records.length == 1) {
                items.push({
                    text: 'View Bag Information',
                    iconCls: 'information-icon',
                    handler: function() {
                        bagGrid.showBagDetails(records[0].get('id'));
                    }
                });
            }

            bagIds = [];
            records.forEach(function(record) {
                bagIds.push(record.get('id'));
            });

            scriptItems = [];
            scriptStore.each(function(record, idx) {
                scriptItems.push({
                    text: record.get('name'),
                    iconCls: 'script-icon',
                    handler: Ext.Function.pass(scriptStore.runScript, [record.get('id'), bagIds])
                });
            });

            items = items.concat([{
                text: 'Display Bag' + pluralSuffix +' on Map',
                iconCls: 'map-icon',
                handler: function() {
                    bagGrid.displayBagsOnMap(records);
                }
            }, {
                text: 'Add Tag',
                iconCls: 'tag-add-icon',
                handler: function() {
                    bagGrid.addTag(records);
                }
            }, {
                text: 'Download Bag' + pluralSuffix,
                iconCls: 'save-icon',
                handler: function() {
                    bagGrid.downloadBags(records);
                }
            }, {
                text: 'Copy Link' + pluralSuffix,
                iconCls: 'link-icon',
                handler: function() {
                    var links = [];
                    records.forEach(function(record) {
                        links.push(document.location.href +
                            'bags/download?bagId=' +
                             record.get('bagId'));
                    });
                    bagGrid.copyTextToClipboard(links.join('\n'));
                }
            }, {
                text: 'Process Bag' + pluralSuffix + ' with Script',
                iconCls: 'script-go-icon',
                disabled: scriptItems.length == 0,
                menu: {
                    items: scriptItems
                }
            }]);
            Ext.create('Ext.menu.Menu', {
                items: items
            }).showAt(event.getXY());
            event.preventDefault();
        },
        rowdblclick: function(rowmodel, record) {
            var bagId = record.get('bagId');
            if (bagId) {
                rowmodel.up('viewport').down('bagGrid').showBagDetails(bagId);
            }
        },
        selectionchange: function(rowmodel, records, index) {
            var treepanel, isDisabled, copyLinkButton, mapBagButton, downloadBagButton;
            treepanel = rowmodel.view.up('treepanel');

            records = treepanel.getBagRecords(records);

            isDisabled = !(records && records.length > 0);
            copyLinkButton = treepanel.down('#copyLinkButton');
            mapBagButton = treepanel.down('#mapBagButton');
            downloadBagButton = treepanel.down('#downloadButton');

            // Only allow "View Info" for one bag at a time
            treepanel.down('#infoButton').setDisabled(isDisabled || records.length > 1);
            treepanel.down('#addTagButton').setDisabled(isDisabled);
            copyLinkButton.setDisabled(isDisabled);
            mapBagButton.setDisabled(isDisabled);
            downloadBagButton.setDisabled(isDisabled);
            treepanel.down('#runScriptButton').setDisabled(isDisabled);

            if (records.length > 1) {
                copyLinkButton.setText("Copy Links");
                mapBagButton.setText("Map Bags");
                downloadBagButton.setText("Download Bags");
            }
            else {
                copyLinkButton.setText("Copy Link");
                mapBagButton.setText("Map Bag");
                downloadBagButton.setText("Download Bag");
            }
         }
    },
    initComponent: function() {
        var me = this;
        Ext.apply(this, {
            store: Ext.create('BagDatabase.stores.BagTreeStore', {
                xtype: 'bagTreeStore',
                storeId: 'bagNodeStore',
                listeners: {
                    filterchange: function() {
                        me.updateFilteredBagCounts();
                    },
                    load: function() {
                        me.updateFilteredBagCounts();
                    }
                }
            })
        });

        this.callParent(arguments);
    }
});
