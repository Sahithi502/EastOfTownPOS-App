const {createHash}=require('node:crypto');
const MIME={pdf:'application/pdf',png:'image/png'};
function validateReceipt(bytes,format,contentType){
  const fail=()=>{throw Error('The receipt export is not a valid '+String(format).toUpperCase()+' file. Nothing was shared. Save the receipt again or try the other format.');};
  if(!MIME[format]||!Buffer.isBuffer(bytes)||bytes.length<32||bytes.length>32*1024*1024)fail();
  if(contentType!==undefined&&String(contentType).split(';')[0].trim().toLowerCase()!==MIME[format])fail();
  if(format==='pdf'){
    if(!/^%PDF-(?:1\.[0-9]|2\.0)/.test(bytes.subarray(0,8).toString('ascii'))||!bytes.subarray(-1024).includes(Buffer.from('%%EOF')))fail();
  }else{
    if(!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))fail();
    let offset=8,first=true,ended=false;
    while(offset+12<=bytes.length){
      const length=bytes.readUInt32BE(offset);if(length>bytes.length-offset-12)fail();
      const type=bytes.toString('ascii',offset+4,offset+8);
      if(first&&(type!=='IHDR'||length!==13||!bytes.readUInt32BE(offset+8)||!bytes.readUInt32BE(offset+12)))fail();
      let crc=0xffffffff;for(const byte of bytes.subarray(offset+4,offset+8+length)){crc^=byte;for(let bit=0;bit<8;bit++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}
      if(((crc^0xffffffff)>>>0)!==bytes.readUInt32BE(offset+8+length))fail();
      offset+=length+12;first=false;if(type==='IEND'){ended=length===0&&offset===bytes.length;break;}
    }
    if(!ended)fail();
  }
  return {format,mime:MIME[format],bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')};
}
module.exports={validateReceipt};
