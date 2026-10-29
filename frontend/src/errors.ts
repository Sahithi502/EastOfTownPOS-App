/** Remove transport wrappers while preserving the actual validation message. */
export function errorMessage(error:unknown):string{
 let message=typeof error==='string'?error:error instanceof Error?error.message:
  error&&typeof error==='object'&&'message' in error&&typeof error.message==='string'?error.message:'';
 message=message.trim();
 let previous='';
 while(message!==previous){
  previous=message;
  message=message.replace(/^Error:\s*/i,'').replace(/^Error invoking remote method ['"][^'"\r\n]+['"]:\s*/i,'').trim();
 }
 return message||'Something went wrong. Please try again.';
}
