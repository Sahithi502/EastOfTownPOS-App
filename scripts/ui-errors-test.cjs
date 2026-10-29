const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const ts=require('typescript'),exportsObject={};
const source=fs.readFileSync(path.join(__dirname,'../frontend/src/errors.ts'),'utf8');
vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports:exportsObject,Error});
const {errorMessage}=exportsObject;
test('the reported Electron mobile error displays only its actionable message',()=>{
 const message='Enter a valid mobile number for the selected country.';
 assert.equal(errorMessage("Error: Error invoking remote method 'catalog:request': Error: "+message),message);
 assert.equal(errorMessage(new Error("Error invoking remote method 'catalog:request': Error: "+message)),message);
});
test('all supported IPC channels and nested wrappers preserve the failure reason',()=>{
 for(const operation of ['request','upload','download','maintenance'])assert.equal(errorMessage(`Error: Error invoking remote method 'catalog:${operation}': Error: Error: Printer is unavailable.`),'Printer is unavailable.');
 assert.equal(errorMessage('Error invoking remote method "catalog:request": Error: Access denied.'),'Access denied.');
});
test('local validation and meaningful punctuation are preserved',()=>{
 assert.equal(errorMessage(new Error('Enter a non-negative amount with up to two decimal places.')),'Enter a non-negative amount with up to two decimal places.');
 assert.equal(errorMessage('SKU: EOT-123 is already used.'),'SKU: EOT-123 is already used.');
 assert.equal(errorMessage('Printer error: check paper.'),'Printer error: check paper.');
});
test('unexpected or empty errors have a useful fallback',()=>{
 for(const input of [null,undefined,{},'', 'Error: '])assert.equal(errorMessage(input),'Something went wrong. Please try again.');
 assert.equal(errorMessage({message:'Error: Select a customer.'}),'Select a customer.');
});
