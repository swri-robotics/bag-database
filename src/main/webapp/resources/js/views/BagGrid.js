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
                    text: 'Add Tag',
                    iconCls: 'tag-add-icon',
                    handler: function() {
                        grid.ownerCt.addTag([record]);
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
                }, {
                    text: 'Copy Link',
                    iconCls: 'link-icon',
                    handler: function() {
                        grid.ownerCt.copyTextToClipboard(
                            document.location.href +
                            'bags/download?bagId=' +
                             record.get('id'));
                    }
                }];
            }
            else {
                items = [{
                    text: 'Add Tag',
                    iconCls: 'tag-add-icon',
                    handler: function() {
                        grid.ownerCt.addTag(records);
                    }
                }, {
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
                }, {
                    text: 'Copy Links',
                    iconCls: 'link-icon',
                    handler: function() {
                        var links = [];
                        records.forEach(function(record) {
                            links.push(document.location.href +
                                'bags/download?bagId=' +
                                 record.get('id'));
                        });
                        grid.ownerCt.copyTextToClipboard(links.join('\n'));
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
        text: 'File Name', dataIndex: 'filename', flex: 2, filter: { type: 'string' }
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
        renderer: bagGridDateRenderer,
        hidden: true, filter: { type: 'date', dateFormat: 'time' }
    }, {
        text: 'Updated On', dataIndex: 'updatedOn', flex: 1,
        renderer: bagGridDateRenderer,
        hidden: true, filter: { type: 'date', dateFormat: 'time' }
    }, {
        text: 'Start Time', dataIndex: 'startTime', flex: 1, filter: { type: 'date', dateFormat: 'time' },
        renderer: bagGridDateRenderer
    }, {
        text: 'End Time', dataIndex: 'endTime', flex: 1, filter: { type: 'date', dateFormat: 'time' },
        renderer: bagGridDateRenderer
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
        text: 'Tags', dataIndex: 'tags', flex: 1.5, sortable: false,
        renderer: function(value, metadata, record) {
            var strTags = [];
            record.tags().each(function(tag) {
                var key = Ext.String.htmlEncode(tag.get('tag'));
                var value = tag.get('value');
                if (value) {
                    value = Ext.String.htmlEncode(value);
                }
                strTags.push(key + (value ? (': ' + value) : value));
            });

            strTags = strTags.sort(function (a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); });
            metadata.tdAttr = 'data-qtip="' + strTags.join("<br>") + '"';
            return strTags.join(", ");
        }
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

    addTag: function(bagRecords) {
        var bagIds = [];
        bagRecords.forEach(function(record) {
            bagIds.push(record.get('id'));
        });

        var win = Ext.create('BagDatabase.views.SetTagWindow', {
            bagIds: bagIds,
            targetStores: [this.getStore(),
                this.up('viewport').down('bagTreePanel').getStore()]
        });
        win.show();
    },
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
                if (response.status == 401) {
                    Ext.Msg.alert('Session Timeout', 'Your session has timed out.  Please reload.');
                    return;
                }
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
    copyTextToClipboard: function(text) {
      var textArea = document.createElement("textarea");

      //
      // *** This styling is an extra step which is likely not required. ***
      //
      // Why is it here? To ensure:
      // 1. the element is able to have focus and selection.
      // 2. if element was to flash render it has minimal visual impact.
      // 3. less flakyness with selection and copying which **might** occur if
      //    the textarea element is not visible.
      //
      // The likelihood is the element won't even render, not even a flash,
      // so some of these are just precautions. However in IE the element
      // is visible whilst the popup box asking the user for permission for
      // the web page to copy to the clipboard.
      //

      // Place in top-left corner of screen regardless of scroll position.
      textArea.style.position = 'fixed';
      textArea.style.top = 0;
      textArea.style.left = 0;

      // Ensure it has a small width and height. Setting to 1px / 1em
      // doesn't work as this gives a negative w/h on some browsers.
      textArea.style.width = '2em';
      textArea.style.height = '2em';

      // We don't need padding, reducing the size if it does flash render.
      textArea.style.padding = 0;

      // Clean up any borders.
      textArea.style.border = 'none';
      textArea.style.outline = 'none';
      textArea.style.boxShadow = 'none';

      // Avoid flash of white box if rendered for any reason.
      textArea.style.background = 'transparent';


      textArea.value = text;

      document.body.appendChild(textArea);

      textArea.select();

      try {
        var successful = document.execCommand('copy');
        var msg = successful ? 'successful' : 'unsuccessful';
        console.log('Copying text command was ' + msg);
      } catch (err) {
        console.log('Oops, unable to copy');
      }

      document.body.removeChild(textArea);
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
                        me.setTitle(me.baseTitle + ' (' + store.getCount() + ' bags)');
                    }
                }
            })
        });

        this.callParent(arguments);

        this.baseTitle = this.title;
    }
});