/* Minimal ZIP writer: UTF-8 names, stored entries, CRC32, no external runtime library. */
'use strict';
window.makeProjectZip = function(files) {
  const enc=new TextEncoder(), table=new Uint32Array(256);
  for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;table[n]=c>>>0;}
  const crc=b=>{let c=0xffffffff;for(const v of b)c=table[(c^v)&255]^(c>>>8);return (c^0xffffffff)>>>0;};
  const parts=[],central=[];let offset=0,centralSize=0;
  for(const [path,text] of Object.entries(files)){
    const name=enc.encode(path),data=enc.encode(text),sum=crc(data);
    const head=new Uint8Array(30+name.length),h=new DataView(head.buffer);
    h.setUint32(0,0x04034b50,true);h.setUint16(4,20,true);h.setUint16(6,0x0800,true);h.setUint16(12,33,true);
    h.setUint32(14,sum,true);h.setUint32(18,data.length,true);h.setUint32(22,data.length,true);h.setUint16(26,name.length,true);head.set(name,30);
    parts.push(head,data);
    const entry=new Uint8Array(46+name.length),e=new DataView(entry.buffer);
    e.setUint32(0,0x02014b50,true);e.setUint16(4,20,true);e.setUint16(6,20,true);e.setUint16(8,0x0800,true);e.setUint16(14,33,true);
    e.setUint32(16,sum,true);e.setUint32(20,data.length,true);e.setUint32(24,data.length,true);e.setUint16(28,name.length,true);e.setUint32(42,offset,true);entry.set(name,46);
    central.push(entry);centralSize+=entry.length;offset+=head.length+data.length;
  }
  const end=new Uint8Array(22),v=new DataView(end.buffer),count=Object.keys(files).length;
  v.setUint32(0,0x06054b50,true);v.setUint16(8,count,true);v.setUint16(10,count,true);v.setUint32(12,centralSize,true);v.setUint32(16,offset,true);
  return new Blob([...parts,...central,end],{type:'application/zip'});
};
