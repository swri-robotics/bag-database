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

Ext.define('BagDatabase.views.BagUploadWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.bagUploadWindow',
    layout: 'fit',
    title: 'Upload Bags',
    iconCls: 'bag-add-icon',
    width: 500,
    height: 400,
    constrainHeader: true,
    items: [{
        multiSelect: true,
        xtype: 'grid',
        store: {
            //storeId: 'bagUploadStore',
            fields: ['name', 'size', 'file', 'status']
        },
        columns: [{
            header: 'Name',
            dataIndex: 'name',
            flex: 2
        }, {
            header: 'Size',
            dataIndex: 'size',
            flex: 1,
            renderer: Ext.util.Format.fileSize
        }, {
            header: 'Status',
            dataIndex: 'status',
            flex: 1,
            renderer: function(value, metaData, record, rowIndex, colIndex, store) {
                // TODO pjr Would be cool if there was a progress bar for uploads here
                var color = "grey";
                if (value === "Ready") {
                    color = "blue";
                } else if (value === "Uploading") {
                    color = "orange";
                } else if (value === "Uploaded") {
                    color = "green";
                } else if (value.startsWith("Error")) {
                    color = "red";
                }
                metaData.tdStyle = 'color:' + color + ";";
                return value;
            }
        }],

        viewConfig: {
            emptyText: 'Drop Files Here',
            deferEmptyText: false
        },

        listeners: {
            drop: { element: 'el', fn: 'drop' },
            dragstart: { element: 'el', fn: 'addDropZone' },
            dragenter: { element: 'el', fn: 'addDropZone' },
            dragover: { element: 'el', fn: 'addDropZone' },
            dragleave: { element: 'el', fn: 'removeDropZone' },
            dragexit: { element: 'el', fn: 'removeDropZone' },
        },

        noop: function(e) {
            e.stopEvent();
        },

        addDropZone: function(e) {
            if (!e.browserEvent.dataTransfer || Ext.Array.from(e.browserEvent.dataTransfer.types).indexOf('Files') === -1) {
                return;
            }

            e.stopEvent();

            this.addCls('drag-over');
        },

        removeDropZone: function(e) {
            var el = e.getTarget(),
              thisEl = this.getEl();

            e.stopEvent();

            if (el === thisEl.dom) {
                this.removeCls('drag-over');
                return;
            }

            while (el !== thisEl.dom && el && el.parentNode) {
                el = el.parentNode;
            }

            if (el !== thisEl.dom) {
                this.removeCls('drag-over');
            }
        },

        drop: function(e) {
            var store = this.up('grid').store;//Ext.getStore('bagUploadStore');
            e.stopEvent();
            Ext.Array.forEach(Ext.Array.from(e.browserEvent.dataTransfer.files), function(file) {
                if (!file.name.endsWith(".bag")) {
                    Ext.Msg.show({
                        title: 'Not a Bag File',
                        message: 'Only uploading .bag files is allowed.',
                        buttons: Ext.Msg.OK,
                        icon: Ext.Msg.WARN
                    });
                    return false;
                }
                store.add({
                    file: file,
                    name: file.name,
                    size: file.size,
                    status: 'Ready'
                });
            });
            this.removeCls('drag-over');
        },

        tbar: [{
            text: "Upload All",
            cls: 'x-btn-default-small',
            listeners: {
                'afterrender': function(field) {
                    field.removeCls('x-btn-default-toolbar-small');
                    field.btnInnerEl.removeCls('x-btn-inner-default-toolbar-small');
                    field.btnInnerEl.addCls('x-btn-inner-default-small');
                }
            },
            handler: function() {
                var grid, store, postDocument, path;
                grid = this.up('grid');
                store = grid.store;
                postDocument = grid.postDocument;
                path = grid.down('#targetPath').getRawValue();
                storageId = grid.down('#storageId').getRawValue();
                store.each(function(item) {
                    if (item.get('status') !== 'Uploaded') {
                        item.set('status', 'Uploading');
                        item.commit();
                        postDocument('bags/upload', item, path, storageId);
                    }
                });
            }
        }, {
            text: "Clear All",
            cls: 'x-btn-default-small',
            listeners: {
                'afterrender': function(field) {
                    field.removeCls('x-btn-default-toolbar-small');
                    field.btnInnerEl.removeCls('x-btn-inner-default-toolbar-small');
                    field.btnInnerEl.addCls('x-btn-inner-default-small');
                }
            },
            handler: function() {
                var store = this.up('grid').store;
                store.reload();
            }
        }, {
            text: "Clear Finished",
            cls: 'x-btn-default-small',
            listeners: {
                'afterrender': function(field) {
                    field.removeCls('x-btn-default-toolbar-small');
                    field.btnInnerEl.removeCls('x-btn-inner-default-toolbar-small');
                    field.btnInnerEl.addCls('x-btn-inner-default-small');
                }
            },
            handler: function() {
                var store, record, i;
                store = this.up('grid').store;
                for (i = 0; i < store.data.items.length; i++) {
                    record = store.getData().getAt(i);
                    if ((record.get('status') === "Uploaded")) {
                        store.remove(record);
                        i--;
                    }
                }
            }
        }, {
            xtype: 'filefield',
            buttonOnly: true,
            buttonText: 'Browse Files...',
            listeners: {
                'afterrender': function(field) {
                    this.fileInputEl.set({ multiple: 'multiple' });
                },
                'change': function(field, path) {
                    var store = this.up('grid').store;

                    Ext.Array.forEach(Ext.Array.from(field.fileInputEl.dom.files), function(file) {
                        if (!file.name.endsWith(".bag")) {
                            Ext.Msg.show({
                                title: 'Not a Bag File',
                                message: 'Only uploading .bag files is allowed.',
                                buttons: Ext.Msg.OK,
                                icon: Ext.Msg.WARN
                            });
                            return false;
                        }
                        store.add({
                            file: file,
                            name: file.name,
                            size: file.size,
                            status: 'Ready'
                        });
                    });
                }
            }
        }],
        dockedItems: [{
            xtype: 'panel',
            layout: 'vbox',
            dock: 'bottom',
            padding: 6,
            items: [{
                xtype: 'combobox',
                fieldLabel: 'Target Path',
                itemId: 'targetPath',
                width: '100%',
                labelWidth: 110,
                allowOnlyWhitespace: false,
                store: {
                    fields: ['path'],
                    proxy: {
                        type: 'ajax',
                        url: 'bags/paths',
                        reader: {
                            type: 'json',
                            rootProperty: 'paths',
                            totalProperty: 'totalCount'
                        },
                        simpleSortMode: true
                    },
                    autoLoad: true
                },
                displayField: 'path',
                valueField: 'path',
                value: '/'
            }, {
                xtype: 'combobox',
                fieldLabel: 'Storage Backend',
                itemId: 'storageId',
                width: '100%',
                labelWidth: 110,
                editable: false,
                allowOnlyWhitespace: false,
                store: {
                    fields: ['storageId'],
                    proxy: {
                        type: 'ajax',
                        url: 'bags/get_storage_ids',
                        reader: {
                            type: 'json',
                            rootProperty: 'storageIds',
                            totalProperty: 'totalCount'
                        },
                        simpleSortMode: true
                    },
                    autoLoad: true
                },
                displayField: 'storageId',
                valueField: 'storageId',
                value: ''
            }]
        }],
        postDocument: function(url, item, path, storageId) {
            var xhr, fd;
            xhr = new XMLHttpRequest();
            fd = new FormData();
            fd.append("serverTimeDiff", 0);
            xhr.open("POST", url, true);

            fd.append('targetDirectory', path);
            fd.append('storageId', storageId);
            fd.append(csrfName, csrfToken);
            fd.append('file', item.get('file'));
            xhr.setRequestHeader("serverTimeDiff", 0);
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        //handle the answer, in order to detect any server side error
                        var response = Ext.decode(xhr.responseText);
                        if (response.success) {
                            item.set('status', 'Uploaded');
                        }
                        else {
                            item.set('status', 'Error: ' + response.message);
                        }
                    }
                    else if (xhr.status === 500) {
                        item.set('status', 'Error');
                    }
                    else if (xhr.status === 0) {
                        item.set('status', 'Max upload size (50GB) exceeded');
                    }
                    else {
                        item.set('status', 'Unknown');
                    }
                    item.commit();
                }
            };
            // Initiate a multipart/form-data upload
            xhr.send(fd);
        }
    }]
});
