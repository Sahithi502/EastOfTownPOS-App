const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('catalog', { request: (method, route, body) => ipcRenderer.invoke('catalog:request', method, route, body) });
