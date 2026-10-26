const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('catalog', {
  request: (method, route, body) => ipcRenderer.invoke('catalog:request', method, route, body),
  upload: () => ipcRenderer.invoke('catalog:upload'),
  download: input => ipcRenderer.invoke('catalog:download',input),
  maintenance: input => ipcRenderer.invoke('catalog:maintenance',input)
});
