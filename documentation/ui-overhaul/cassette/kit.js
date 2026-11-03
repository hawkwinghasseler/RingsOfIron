// Yard Standard mockup kit: palette, SVG defs and the console primitives.
// Shared by cassette/mockups.html and the variations in documentation/ui-overhaul/variations/.

// ---------- palette ----------
const C = {
  enamel:'#D9CFB4', enamelShade:'#B8AD92', enamelLight:'#ECE4CE', chassis:'#4A3F36', chassisDeep:'#2B241F',
  bakelite:'#1E1A17', alu:'#A9A69E', rubber:'#3A3734', ink:'#2A2420', inkLight:'#EFE8D6',
  orange:'#D9602B', red:'#B8352B', mustard:'#D9A637', avocado:'#6E7846',
  amber:'#FFB23F', amberDim:'#9A6420', amberGhost:'#4A300F', green:'#7CFF6A', greenDim:'#2F8A35',
  crtBlack:'#0E100C', crtOff:'#22251F',
  lampRed:'#FF4A35', lampRedOff:'#5A1E18', lampAmber:'#FFA733', lampAmberOff:'#5A3B16',
  lampGreen:'#8CFF5A', lampGreenOff:'#24401C', lampWhite:'#FFF3D6', lampWhiteOff:'#4A4740',
  paper:'#EEE6D2'
};
let seed = 7;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;

const DEFS = `
<defs>
  <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
    <feGaussianBlur in="SourceGraphic" stdDeviation="2.2" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="lampglow" x="-60%" y="-60%" width="220%" height="220%">
    <feGaussianBlur in="SourceGraphic" stdDeviation="6" result="b"/>
    <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="grain" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="3" result="n"/>
    <feColorMatrix in="n" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.07 0" result="g"/>
    <feComposite in="g" in2="SourceGraphic" operator="in" result="gi"/>
    <feMerge><feMergeNode in="SourceGraphic"/><feMergeNode in="gi"/></feMerge>
  </filter>
  <filter id="shadow" x="-10%" y="-10%" width="130%" height="140%">
    <feDropShadow dx="1.5" dy="2.5" stdDeviation="1.6" flood-color="#000" flood-opacity="0.55"/>
  </filter>
  <filter id="deckshadow" x="-5%" y="-40%" width="110%" height="180%">
    <feDropShadow dx="0" dy="-8" stdDeviation="14" flood-color="#000" flood-opacity="0.9"/>
  </filter>
  <linearGradient id="enamelG" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#E3DAC1"/><stop offset="1" stop-color="#CFC4A8"/>
  </linearGradient>
  <linearGradient id="avoG" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#7A8450"/><stop offset="1" stop-color="#646D3F"/>
  </linearGradient>
  <linearGradient id="chassisG" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#52463C"/><stop offset="1" stop-color="#3C332B"/>
  </linearGradient>
  <linearGradient id="lipG" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#F4EEDD"/><stop offset="0.35" stop-color="#D9CFB4"/><stop offset="1" stop-color="#9F957C"/>
  </linearGradient>
  <radialGradient id="knobG" cx="0.35" cy="0.3" r="0.8">
    <stop offset="0" stop-color="#4a423c"/><stop offset="0.5" stop-color="#221d1a"/><stop offset="1" stop-color="#110e0c"/>
  </radialGradient>
  <radialGradient id="screwG" cx="0.35" cy="0.3" r="0.8">
    <stop offset="0" stop-color="#d8d5cc"/><stop offset="1" stop-color="#7d7a72"/>
  </radialGradient>
  <linearGradient id="glassG" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#fff" stop-opacity="0.10"/><stop offset="0.35" stop-color="#fff" stop-opacity="0.02"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="vignette" cx="0.5" cy="0.5" r="0.72">
    <stop offset="0.65" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.55"/>
  </radialGradient>
  <linearGradient id="drumG" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#050505"/><stop offset="0.25" stop-color="#2a2725"/><stop offset="0.5" stop-color="#34302d"/><stop offset="0.75" stop-color="#2a2725"/><stop offset="1" stop-color="#050505"/>
  </linearGradient>
  <pattern id="scan" width="4" height="3" patternUnits="userSpaceOnUse">
    <rect width="4" height="1" y="2" fill="#000" opacity="0.35"/>
  </pattern>
  <pattern id="hazard" width="16" height="16" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
    <rect width="8" height="16" fill="${'#D9A637'}"/><rect x="8" width="8" height="16" fill="#1E1A17"/>
  </pattern>
  <pattern id="barber" width="10" height="10" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
    <rect width="5" height="10" fill="#e8e2d0"/><rect x="5" width="5" height="10" fill="#B8352B"/>
  </pattern>
  <pattern id="greenbar" width="10" height="44" patternUnits="userSpaceOnUse">
    <rect width="10" height="22" fill="#d6e6cc" opacity="0.55"/>
  </pattern>
</defs>`;

// ---------- primitives ----------
function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;'); }
function screw(x,y){
  const a = rnd()*180;
  return `<g transform="translate(${x},${y}) rotate(${a})"><circle r="5" fill="url(#screwG)" stroke="#5d5a53" stroke-width="0.8"/>
  <path d="M-3 0H3M0 -3V3" stroke="#4b4842" stroke-width="1.4"/></g>`;
}
function plate(x,y,w,h,{fill='url(#enamelG)', title=null, titleColor=C.ink, screws=true, r=3}={}){
  let s = `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" filter="url(#grain)"/></g>
  <path d="M${x+r} ${y+0.8}H${x+w-r}" stroke="${C.enamelLight}" stroke-width="1.6" opacity="0.9"/>
  <path d="M${x+0.8} ${y+r}V${y+h-r}" stroke="${C.enamelLight}" stroke-width="1.2" opacity="0.6"/>
  <path d="M${x+r} ${y+h-0.8}H${x+w-r}M${x+w-0.8} ${y+r}V${y+h-r}" stroke="#7d735e" stroke-width="1.4" opacity="0.8"/>`;
  if (screws) s += screw(x+8,y+8)+screw(x+w-8,y+8)+screw(x+8,y+h-8)+screw(x+w-8,y+h-8);
  if (title) s += `<text x="${x+20}" y="${y+22}" class="legend" font-size="13" fill="${titleColor}">${esc(title)}</text>`;
  return s;
}
function legend(x,y,t,{size=11,anchor='middle',fill=C.ink}={}){
  return `<text x="${x}" y="${y}" class="legend" font-size="${size}" text-anchor="${anchor}" fill="${fill}">${esc(t)}</text>`;
}
function keycap(x,y,k){
  const w = Math.max(16, 8 + k.length*7);
  return `<g transform="translate(${x-w/2},${y})"><rect width="${w}" height="15" rx="3" fill="${C.rubber}"/>
  <rect x="1.5" y="1" width="${w-3}" height="11" rx="2" fill="#4a4642"/>
  <text x="${w/2}" y="10.5" text-anchor="middle" class="drum" font-size="10" fill="${C.inkLight}">${esc(k)}</text></g>`;
}
function dymo(x,y,t,{color='#9E2A23', rot=-1, size=14}={}){
  const w = t.length*size*0.62 + 18;
  return `<g transform="translate(${x},${y}) rotate(${rot})" filter="url(#shadow)">
    <rect x="0" y="0" width="${w}" height="${size+10}" rx="2" fill="${color}"/>
    <rect x="0" y="0" width="${w}" height="${size+10}" rx="2" fill="url(#glassG)"/>
    <text x="${w/2}" y="${size+3}" text-anchor="middle" class="dymo" font-size="${size}" fill="#000" opacity="0.35" transform="translate(0.8,1)">${esc(t)}</text>
    <text x="${w/2}" y="${size+3}" text-anchor="middle" class="dymo" font-size="${size}" fill="#f4efe6">${esc(t)}</text></g>`;
}

// round needle gauge, 270 deg sweep
function needle(cx,cy,r,{min=0,max=100,value=50,major=5,minor=4,zones=[],label='',unit='',fmt=v=>v,park=false,outside=false}={}){
  const a0 = 150, a1 = 390;
  const ang = v => a0 + (Math.max(min,Math.min(max,v))-min)/(max-min)*(a1-a0);
  const pt = (a,rr) => [cx + rr*Math.cos(a*Math.PI/180), cy + rr*Math.sin(a*Math.PI/180)];
  let s = `<circle cx="${cx}" cy="${cy}" r="${r+9}" fill="${C.bakelite}"/><circle cx="${cx}" cy="${cy}" r="${r+9}" fill="url(#glassG)"/>
  <circle cx="${cx}" cy="${cy}" r="${r+2}" fill="#F2ECDC"/>`;
  for (const z of zones){
    const [x0,y0]=pt(ang(z.from),r-6),[x1,y1]=pt(ang(z.to),r-6);
    const large = (ang(z.to)-ang(z.from))>180?1:0;
    s += `<path d="M${x0} ${y0}A${r-6} ${r-6} 0 ${large} 1 ${x1} ${y1}" stroke="${z.color}" stroke-width="7" fill="none"/>`;
  }
  const steps = major*minor;
  for (let i=0;i<=steps;i++){
    const v = min + (max-min)*i/steps, a = ang(v), big = i%minor===0;
    const [x0,y0]=pt(a,r-1),[x1,y1]=pt(a,r-(big?13:7));
    s += `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${C.ink}" stroke-width="${big?2:1}"/>`;
    if (big){ const [tx,ty]=pt(a,r-(r>50?24:20)); s += `<text x="${tx}" y="${ty+3.5}" text-anchor="middle" class="legend" font-size="${r>50?11:8.5}" fill="${C.ink}">${fmt(v)}</text>`; }
  }
  if (outside){ s += legend(cx,cy+r+24,label + (unit?'  '+unit:''),{size:10}); }
  else { s += legend(cx,cy+r*0.6,label,{size:12}); if (unit) s += legend(cx,cy+r*0.6+12,unit,{size:9,fill:'#6b6254'}); }
  const va = park ? a0-8 : ang(value);
  const [nx,ny]=pt(va,r-8),[bx,by]=pt(va+180,14);
  s += `<g filter="url(#shadow)"><line x1="${bx}" y1="${by}" x2="${nx}" y2="${ny}" stroke="#1a1614" stroke-width="2.6" stroke-linecap="round"/>
  <line x1="${cx}" y1="${cy}" x2="${nx}" y2="${ny}" stroke="${C.orange}" stroke-width="1.6" stroke-linecap="round"/>
  <circle cx="${cx}" cy="${cy}" r="6" fill="url(#knobG)"/></g>`;
  s += `<path d="M${cx-r} ${cy-r*0.2}A${r} ${r} 0 0 1 ${cx+r*0.3} ${cy-r}" stroke="#fff" stroke-opacity="0.25" stroke-width="5" fill="none"/>`;
  return s;
}

// vertical edgewise meter
function edgewise(x,y,{h=110,value=0.5,label='',key=null,unitText='',warn=0.5,crit=0.25,park=false}={}){
  const w = 34;
  let s = `<rect x="${x-3}" y="${y-3}" width="${w+6}" height="${h+6}" rx="3" fill="${C.bakelite}"/>
  <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#F2ECDC"/>
  <rect x="${x+w-10}" y="${y+h*(1-warn)}" width="7" height="${h*(warn-crit)}" fill="${C.mustard}"/>
  <rect x="${x+w-10}" y="${y+h*(1-crit)}" width="7" height="${h*crit}" fill="${C.red}"/>`;
  for (let i=0;i<=10;i++){
    const yy = y + h*i/10; const big = i%5===0;
    s += `<line x1="${x+w-10}" y1="${yy}" x2="${x+w-(big?22:16)}" y2="${yy}" stroke="${C.ink}" stroke-width="${big?1.6:0.8}"/>`;
    if (big) s += `<text x="${x+3}" y="${Math.min(Math.max(yy+3,y+8),y+h-1)}" class="legend" font-size="8" fill="${C.ink}">${(10-i)*10}</text>`;
  }
  const py = park ? y+h+2 : y + h*(1-value);
  s += `<g filter="url(#shadow)"><path d="M${x+w-2} ${py}l-14 -5v10z" fill="${C.orange}" stroke="#1a1614" stroke-width="0.8"/><line x1="${x+2}" y1="${py}" x2="${x+w-14}" y2="${py}" stroke="#1a1614" stroke-width="1.2"/></g>
  <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#glassG)"/>`;
  s += legend(x+w/2,y+h+20,label,{size:11});
  if (unitText) s += legend(x+w/2,y+h+33,unitText,{size:9,fill:'#6b6254'});
  return s;
}

// drum counter
function drums(x,y,str,{unit='',label='',dimLead=true,cw=20,ch=32,flag=false,size=24,roll=0}={}){
  const n = str.length;
  const W = [...str].reduce((a,c)=>a+(c==='.'?6:cw),0);
  let off = 0;
  let s = `<rect x="${x-5}" y="${y-5}" width="${W+10}" height="${ch+10}" rx="3" fill="${C.bakelite}"/>`;
  let lead = true;
  for (let i=0;i<n;i++){
    const c = str[i]; const xx = x + off;
    if (c==='.' ){ s += `<circle cx="${xx+3}" cy="${y+ch-6}" r="2" fill="${C.inkLight}"/>`; off += 6; continue; }
    off += cw;
    if (c!=='0' && c!==' ') lead = false;
    if (i===n-1 || str[i+1]==='.') lead = false;
    s += `<rect x="${xx+1}" y="${y}" width="${cw-2}" height="${ch}" fill="url(#drumG)"/>`;
    // last digit rolling: show partial next digit
    const isLast = (i===n-1) && roll>0;
    const col = (dimLead && lead) ? '#77716a' : '#F3EEE2';
    if (isLast){
      const b = ch/2+size*0.36 - roll*ch;
      s += `<svg x="${xx+1}" y="${y}" width="${cw-2}" height="${ch}"><text x="${(cw-2)/2}" y="${b}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${c}</text>
      <text x="${(cw-2)/2}" y="${b+ch}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${(+c+1)%10}</text></svg>`;
    } else {
      s += `<text x="${xx+cw/2}" y="${y+ch/2+size*0.36}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${c===' '?'':c}</text>`;
    }
    s += `<rect x="${xx+1}" y="${y}" width="${cw-2}" height="${ch}" fill="url(#drumG)" opacity="0.35"/>`;
  }
  if (flag) s += `<rect x="${x}" y="${y+ch*0.3}" width="${W}" height="${ch*0.4}" fill="url(#barber)" stroke="#111" stroke-width="1"/>
    <rect x="${x+W/2-18}" y="${y+ch*0.3+1}" width="36" height="${ch*0.4-2}" fill="#e8e2d0"/>${legend(x+W/2,y+ch*0.5+4,'OFF',{size:11})}`;
  s += `<rect x="${x-5}" y="${y-5}" width="${W+10}" height="${ch+10}" rx="3" fill="url(#glassG)"/>`;
  if (unit) s += legend(x+W+10,y+ch/2+5,unit,{size:12,anchor:'start'});
  if (label) s += legend(x-5,y-11,label,{size:11,anchor:'start'});
  return s;
}

// annunciator tile
function tile(x,y,w,h,lines,{color='amber',lit=false}={}){
  const on = {red:C.lampRed,amber:C.lampAmber,green:C.lampGreen,white:C.lampWhite}[color];
  const off = {red:C.lampRedOff,amber:C.lampAmberOff,green:C.lampGreenOff,white:C.lampWhiteOff}[color];
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#0c0a09"/>`;
  if (lit) s += `<rect x="${x+2}" y="${y+2}" width="${w-4}" height="${h-4}" fill="${on}" filter="url(#lampglow)" opacity="0.9"/>`;
  s += `<rect x="${x+2}" y="${y+2}" width="${w-4}" height="${h-4}" fill="${lit?on:off}"/>
  <rect x="${x+2}" y="${y+2}" width="${w-4}" height="${h-4}" fill="url(#glassG)"/>`;
  const tc = lit ? '#2a1606' : 'rgba(240,230,210,0.42)';
  lines.forEach((t,i)=>{ s += `<text x="${x+w/2}" y="${y+h/2 + (i-(lines.length-1)/2)*12 + 4}" text-anchor="middle" class="legend" font-size="10.5" fill="${tc}">${esc(t)}</text>`; });
  return s;
}
function pushLight(x,y,w,h,lines,opts={}){
  return `<rect x="${x-4}" y="${y-4}" width="${w+8}" height="${h+8}" rx="3" fill="${C.alu}"/><rect x="${x-2}" y="${y-2}" width="${w+4}" height="${h+4}" rx="2" fill="#5a5750"/>` + tile(x,y,w,h,lines,opts);
}

// toggle switch
function toggle(x,y,{up=true,label='',key=null,guard=false,guardOpen=false,dark=false,pos=['ON','OFF']}={}){
  let s = '';
  if (guard){
    s += `<rect x="${x-17}" y="${y-30}" width="34" height="60" rx="3" fill="${C.alu}" stroke="#6d6a63"/>`;
  }
  s += `<circle cx="${x}" cy="${y}" r="13" fill="${C.alu}" stroke="#77736b"/><circle cx="${x}" cy="${y}" r="8" fill="#5c5852"/>
  <circle cx="${x}" cy="${y}" r="5" fill="#2a2725"/>`;
  const dy = up ? -1 : 1;
  s += `<g filter="url(#shadow)"><path d="M${x-3.2} ${y}L${x-5} ${y+dy*24}A5 5 0 0 0 ${x+5} ${y+dy*24}L${x+3.2} ${y}Z" fill="url(#knobG)"/>
  <ellipse cx="${x}" cy="${y+dy*24}" rx="5" ry="3.4" fill="#3a3430"/></g>`;
  if (guard){
    if (guardOpen) s += `<g filter="url(#shadow)"><rect x="${x-16}" y="${y-66}" width="32" height="36" rx="4" fill="${C.red}" opacity="0.95"/>
      <rect x="${x-16}" y="${y-66}" width="32" height="36" rx="4" fill="url(#glassG)"/><rect x="${x-16}" y="${y-34}" width="32" height="5" fill="#7a221b"/></g>`;
    else s += `<g filter="url(#shadow)"><rect x="${x-16}" y="${y-29}" width="32" height="58" rx="4" fill="${C.red}" opacity="0.93"/>
      <rect x="${x-16}" y="${y-29}" width="32" height="58" rx="4" fill="url(#glassG)"/>
      <path d="M${x-10} ${y-20}H${x+10}" stroke="#7a221b" stroke-width="2"/></g>`;
  }
  if (pos && !guard){ s += legend(x+22,y-14,pos[0],{size:9,anchor:'start',fill:'#6b6254'}) + legend(x+22,y+20,pos[1],{size:9,anchor:'start',fill:'#6b6254'}); }
  s += legend(x,y+ (guard?48:46),label,{size:11,fill: dark ? '#8f8775' : C.ink});
  if (key) s += keycap(x,y+(guard?54:52),key);
  return s;
}

// rotary selector
function rotary(cx,cy,r,positions,sel,{label='',arc=[-135,135],key=null,size=10,escut=null}={}){
  let s = '';
  if (escut) s += `<circle cx="${cx}" cy="${cy}" r="${r+30}" fill="${escut}" opacity="0.9"/>`;
  const n = positions.length;
  positions.forEach((p,i)=>{
    const a = (arc[0] + (arc[1]-arc[0])*(n===1?0:i/(n-1)) - 90)*Math.PI/180;
    const x0 = cx + (r+4)*Math.cos(a), y0 = cy + (r+4)*Math.sin(a);
    const x1 = cx + (r+10)*Math.cos(a), y1 = cy + (r+10)*Math.sin(a);
    const tx = cx + (r+20)*Math.cos(a), ty = cy + (r+20)*Math.sin(a);
    s += `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${C.ink}" stroke-width="1.6"/>
    <text x="${tx}" y="${ty+3.5}" text-anchor="middle" class="legend" font-size="${size}" fill="${i===sel?C.ink:'#6b6254'}">${esc(p)}</text>`;
  });
  const a = (arc[0] + (arc[1]-arc[0])*(n===1?0:sel/(n-1)));
  s += `<g filter="url(#shadow)"><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#knobG)"/>`;
  for (let k=0;k<24;k++){ const aa=k*15*Math.PI/180; s+=`<line x1="${cx+(r-1)*Math.cos(aa)}" y1="${cy+(r-1)*Math.sin(aa)}" x2="${cx+(r-4)*Math.cos(aa)}" y2="${cy+(r-4)*Math.sin(aa)}" stroke="#000" stroke-width="1"/>`; }
  s += `<circle cx="${cx}" cy="${cy}" r="${r*0.68}" fill="#2a2420"/>
  <g transform="rotate(${a} ${cx} ${cy})"><rect x="${cx-r*0.17}" y="${cy-r*0.95}" width="${r*0.34}" height="${r*1.9}" rx="${r*0.15}" fill="#14110f"/>
  <line x1="${cx}" y1="${cy-r*0.9}" x2="${cx}" y2="${cy-r*0.45}" stroke="${C.inkLight}" stroke-width="2.2"/></g></g>`;
  if (label) s += legend(cx,cy+r+(positions.length>2?36:22),label,{size:11});
  if (key) s += keycap(cx,cy+r+(positions.length>2?42:28),key);
  return s;
}

// CRT bezel + face
function crtFrame(x,y,w,h,{round=false,lit=true}={}){
  let s='';
  if (round){
    const cx=x+w/2, cy=y+h/2, r=w/2;
    s += `<circle cx="${cx}" cy="${cy}" r="${r+14}" fill="${C.bakelite}"/><circle cx="${cx}" cy="${cy}" r="${r+14}" fill="url(#glassG)"/>
    <circle cx="${cx}" cy="${cy}" r="${r+3}" fill="#090a08"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="${lit?C.crtBlack:C.crtOff}"/>`;
  } else {
    s += `<rect x="${x-14}" y="${y-14}" width="${w+28}" height="${h+28}" rx="10" fill="${C.bakelite}"/>
    <rect x="${x-14}" y="${y-14}" width="${w+28}" height="${h+28}" rx="10" fill="url(#glassG)"/>
    <rect x="${x-3}" y="${y-3}" width="${w+6}" height="${h+6}" rx="16" fill="#090a08"/>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="${lit?C.crtBlack:C.crtOff}"/>`;
  }
  return s;
}
function crtGlass(x,y,w,h,{round=false}={}){
  if (round){ const cx=x+w/2, cy=y+h/2, r=w/2;
    return `<clipPath id="cp${Math.round(x)}${Math.round(y)}"><circle cx="${cx}" cy="${cy}" r="${r}"/></clipPath>
    <g clip-path="url(#cp${Math.round(x)}${Math.round(y)})"><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#scan)"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#vignette)"/></g>
    <ellipse cx="${cx-r*0.35}" cy="${cy-r*0.45}" rx="${r*0.45}" ry="${r*0.22}" transform="rotate(-30 ${cx-r*0.35} ${cy-r*0.45})" fill="#fff" opacity="0.06"/>`; }
  return `<clipPath id="cr${Math.round(x)}${Math.round(y)}"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14"/></clipPath>
  <g clip-path="url(#cr${Math.round(x)}${Math.round(y)})"><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#scan)"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#vignette)"/>
  <path d="M${x} ${y}h${w*0.55}l-${w*0.25} ${h*0.5}h-${w*0.3}z" fill="#fff" opacity="0.035"/></g>`;
}

// raster page text (amber)
function crtText(x,y,rows,{size=15,lh=19,cw=9.0}={}){
  let s = `<g filter="url(#glow)">`;
  rows.forEach((row,i)=>{
    const yy = y + i*lh;
    if (row.rev){ s += `<rect x="${x-3}" y="${yy-size+2}" width="${row.w||row.t.length*cw+6}" height="${lh}" fill="${C.amber}" opacity="0.95"/>
      <text x="${x}" y="${yy}" class="crt" font-size="${size}" fill="${C.crtBlack}" font-weight="500" xml:space="preserve">${esc(row.t)}</text>`; }
    else s += `<text x="${x}" y="${yy}" class="${row.vt?'vt':'crt'}" font-size="${row.size||size}" fill="${row.dim?C.amberDim:C.amber}" xml:space="preserve">${esc(row.t)}</text>`;
  });
  return s + `</g>`;
}

// ---------- shapes on tubes ----------
function sym(kind,x,y,s,col,{fill=false,w=1.6}={}){
  const f = fill?col:'none';
  switch(kind){
    case 'station': return `<rect x="${x-s}" y="${y-s}" width="${2*s}" height="${2*s}" stroke="${col}" stroke-width="${w}" fill="${f}"/>`;
    case 'neutral': return `<path d="M${x} ${y-s*1.2}L${x+s} ${y+s*0.8}H${x-s}Z" stroke="${col}" stroke-width="${w}" fill="${f}"/>`;
    case 'hostile': return `<path d="M${x} ${y-s*1.25}L${x+s*1.1} ${y}L${x} ${y+s*1.25}L${x-s*1.1} ${y}Z" stroke="${col}" stroke-width="${w}" fill="${f}"/>`;
    case 'unknown': return `<circle cx="${x}" cy="${y}" r="${s}" stroke="${col}" stroke-width="${w}" fill="none"/>`;
    case 'own': return `<path d="M${x-s} ${y}H${x+s}M${x} ${y-s}V${y+s}M${x} ${y-s}l0 -${s}" stroke="${col}" stroke-width="${w}"/>`;
    case 'bracket': { const k=s*0.45; return `<path d="M${x-s} ${y-s+k}V${y-s}H${x-s+k}M${x+s-k} ${y-s}H${x+s}V${y-s+k}M${x+s} ${y+s-k}V${y+s}H${x+s-k}M${x-s+k} ${y+s}H${x-s}V${y+s-k}" stroke="${col}" stroke-width="${w}" fill="none"/>`; }
    case 'waypoint': return `<circle cx="${x}" cy="${y}" r="${s}" stroke="${col}" stroke-width="${w}" fill="none"/><path d="M${x-s*0.6} ${y}H${x+s*0.6}M${x} ${y-s*0.6}V${y+s*0.6}" stroke="${col}" stroke-width="${w}"/>`;
    case 'burn': return `<path d="M${x} ${y-s}L${x+s} ${y+s*0.8}H${x-s}Z" stroke="${col}" stroke-width="${w}" fill="none"/>`;
  }
}


// =====================================================================
// GRID KIT: primitives sized for the game's 1280 x 800 UI grid.
// Every piece of text is 12 units or more (tags) and 13 or more (body), the floor the
// functional specs set for legibility at UI scale 1.
// =====================================================================
const PH = { amber: '#FFB23F', dim: '#9A6420', green: '#7CFF6A', gdim: '#2F8A35', red: '#FF5A3C', white: '#F4EAD2' };
const LAMP = {
  red:   ['#FF4A35', '#4E1B16'], amber: ['#FFA733', '#4E3414'],
  green: ['#8CFF5A', '#203A19'], white: ['#FFF3D6', '#47443D']
};

function plateG(x, y, w, h, { title = null, fill = 'url(#enamelG)', screws = true, r = 3, titleFill = C.ink } = {}) {
  let s = `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}"/></g>
  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" filter="url(#grain)"/>
  <path d="M${x + r} ${y + 0.7}H${x + w - r}" stroke="#F4EEDD" stroke-width="1.2" opacity="0.9"/>
  <path d="M${x + r} ${y + h - 0.7}H${x + w - r}M${x + w - 0.7} ${y + r}V${y + h - r}" stroke="#7d735e" stroke-width="1.1" opacity="0.8"/>`;
  if (screws) s += screwG(x + 6, y + 6) + screwG(x + w - 6, y + 6) + screwG(x + 6, y + h - 6) + screwG(x + w - 6, y + h - 6);
  if (title) s += `<text x="${x + 14}" y="${y + 16}" class="legend" font-size="12" fill="${titleFill}">${esc(title)}</text>`;
  return s;
}
function screwG(x, y) {
  const a = rnd() * 180;
  return `<g transform="translate(${x},${y}) rotate(${a})"><circle r="3.2" fill="url(#screwG)" stroke="#5d5a53" stroke-width="0.6"/><path d="M-2 0H2M0 -2V2" stroke="#4b4842" stroke-width="1"/></g>`;
}
function tag(x, y, t, { size = 12, anchor = 'start', fill = C.ink, cls = 'legend' } = {}) {
  return `<text x="${x}" y="${y}" class="${cls}" font-size="${size}" text-anchor="${anchor}" fill="${fill}">${esc(t)}</text>`;
}
function keyG(x, y, k, { anchor = 'middle' } = {}) {
  const w = Math.max(15, 7 + k.length * 7.2), h = 15;
  const x0 = anchor === 'middle' ? x - w / 2 : anchor === 'end' ? x - w : x;
  return `<g transform="translate(${x0},${y})"><rect width="${w}" height="${h}" rx="2.5" fill="${C.rubber}"/>
  <rect x="1.2" y="0.8" width="${w - 2.4}" height="${h - 4}" rx="2" fill="#4d4945"/>
  <text x="${w / 2}" y="${h - 4.6}" text-anchor="middle" class="drum" font-size="12" fill="${C.inkLight}">${esc(k)}</text></g>`;
}
function lampG(cx, cy, r, color, lit) {
  const [on, off] = LAMP[color];
  return `${lit ? `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${on}" filter="url(#lampglow)"/>` : ''}
  <circle cx="${cx}" cy="${cy}" r="${r + 1.6}" fill="${C.alu}"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="${lit ? on : off}"/>
  <circle cx="${cx - r * 0.3}" cy="${cy - r * 0.35}" r="${r * 0.35}" fill="#fff" opacity="${lit ? 0.5 : 0.12}"/>`;
}
let TUBE_N = 0;
// A tube: bezel and face. Returns {under, over}: draw content between them.
function tubeG(x, y, w, h, { r = 7, bezel = 6, dead = false, round = false } = {}) {
  let under, over;
  const id = 'tube' + (TUBE_N++);
  if (round) {
    const cx = x + w / 2, cy = y + h / 2, rr = w / 2;
    under = `<circle cx="${cx}" cy="${cy}" r="${rr + bezel}" fill="${C.bakelite}"/><circle cx="${cx}" cy="${cy}" r="${rr + bezel}" fill="url(#glassG)"/>
      <circle cx="${cx}" cy="${cy}" r="${rr + 1.5}" fill="#070806"/><circle cx="${cx}" cy="${cy}" r="${rr}" fill="${dead ? C.crtOff : C.crtBlack}"/>
      <clipPath id="${id}"><circle cx="${cx}" cy="${cy}" r="${rr}"/></clipPath><g clip-path="url(#${id})">`;
    over = `</g><g clip-path="url(#${id})"><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#scan)"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#vignette)"/></g>
      <ellipse cx="${cx - rr * 0.35}" cy="${cy - rr * 0.45}" rx="${rr * 0.45}" ry="${rr * 0.2}" transform="rotate(-30 ${cx - rr * 0.35} ${cy - rr * 0.45})" fill="#fff" opacity="0.06"/>`;
  } else {
    under = `<rect x="${x - bezel}" y="${y - bezel}" width="${w + 2 * bezel}" height="${h + 2 * bezel}" rx="${r + 2}" fill="${C.bakelite}"/>
      <rect x="${x - bezel}" y="${y - bezel}" width="${w + 2 * bezel}" height="${h + 2 * bezel}" rx="${r + 2}" fill="url(#glassG)"/>
      <rect x="${x - 1.5}" y="${y - 1.5}" width="${w + 3}" height="${h + 3}" rx="${r + 1}" fill="#070806"/>
      <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${dead ? C.crtOff : C.crtBlack}"/>
      <clipPath id="${id}"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}"/></clipPath><g clip-path="url(#${id})">`;
    over = `</g><g clip-path="url(#${id})"><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#scan)"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#vignette)"/>
      <path d="M${x} ${y}h${w * 0.5}l-${w * 0.22} ${h * 0.55}h-${w * 0.28}z" fill="#fff" opacity="0.03"/></g>`;
  }
  return { under, over };
}
// Text on a tube (beam-penetration phosphor: amber words, green geometry, red threat)
function tt(x, y, t, { c = 'amber', size = 13, anchor = 'start', rev = false, w = null, weight = 400, glow = true } = {}) {
  const col = PH[c] || c;
  if (rev) {
    const ww = w || t.length * size * 0.6 + 6;
    const x0 = anchor === 'end' ? x - ww + 3 : anchor === 'middle' ? x - ww / 2 : x - 3;
    return `<g filter="url(#glow)"><rect x="${x0}" y="${y - size + 1.5}" width="${ww}" height="${size + 3}" fill="${col}"/></g>
      <text x="${x}" y="${y}" class="crt" font-size="${size}" text-anchor="${anchor}" font-weight="500" fill="${C.crtBlack}" xml:space="preserve">${esc(t)}</text>`;
  }
  return `<text x="${x}" y="${y}" class="crt" font-size="${size}" text-anchor="${anchor}" font-weight="${weight}" fill="${col}" ${glow ? 'filter="url(#glow)"' : ''} xml:space="preserve">${esc(t)}</text>`;
}
// Drum counter (grid sizes)
function drumG(x, y, str, { cw = 15, ch = 22, size = 16, dimLead = true, roll = 0, unit = null, unitGap = 6, flag = false } = {}) {
  const W = [...str].reduce((a, c) => a + (c === '.' ? 5 : c === ':' ? 6 : cw), 0);
  let s = `<rect x="${x - 3}" y="${y - 3}" width="${W + 6}" height="${ch + 6}" rx="2.5" fill="${C.bakelite}"/>`;
  let off = 0, lead = true;
  for (let i = 0; i < str.length; i++) {
    const c = str[i], xx = x + off;
    if (c === '.') { s += `<circle cx="${xx + 2.5}" cy="${y + ch - 4}" r="1.6" fill="${C.inkLight}"/>`; off += 5; continue; }
    if (c === ':') { s += `<circle cx="${xx + 3}" cy="${y + ch * 0.35}" r="1.4" fill="${C.inkLight}"/><circle cx="${xx + 3}" cy="${y + ch * 0.68}" r="1.4" fill="${C.inkLight}"/>`; off += 6; lead = false; continue; }
    if (c !== '0' && c !== ' ') lead = false;
    if (i === str.length - 1 || str[i + 1] === '.' || str[i + 1] === ':') lead = false;
    off += cw;
    const col = dimLead && lead ? '#77716a' : '#F3EEE2';
    s += `<rect x="${xx + 0.8}" y="${y}" width="${cw - 1.6}" height="${ch}" fill="url(#drumG)"/>`;
    const base = y + ch / 2 + size * 0.36;
    if (roll && i === str.length - 1 && c !== ' ') {
      s += `<svg x="${xx + 0.8}" y="${y}" width="${cw - 1.6}" height="${ch}"><text x="${(cw - 1.6) / 2}" y="${ch / 2 + size * 0.36 - roll * ch}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${c}</text><text x="${(cw - 1.6) / 2}" y="${ch / 2 + size * 0.36 - roll * ch + ch}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${(+c + 1) % 10}</text></svg>`;
    } else if (c !== ' ') s += `<text x="${xx + cw / 2}" y="${base}" text-anchor="middle" class="drum" font-size="${size}" fill="${col}">${c}</text>`;
    s += `<rect x="${xx + 0.8}" y="${y}" width="${cw - 1.6}" height="${ch}" fill="url(#drumG)" opacity="0.3"/>`;
  }
  if (flag) s += `<rect x="${x}" y="${y + ch * 0.22}" width="${W}" height="${ch * 0.56}" fill="url(#barber)" stroke="#111" stroke-width="0.8"/><rect x="${x + W / 2 - 16}" y="${y + ch * 0.22 + 1}" width="32" height="${ch * 0.56 - 2}" fill="#e8e2d0"/>${tag(x + W / 2, y + ch * 0.5 + 4.5, 'OFF', { anchor: 'middle' })}`;
  s += `<rect x="${x - 3}" y="${y - 3}" width="${W + 6}" height="${ch + 6}" rx="2.5" fill="url(#glassG)"/>`;
  if (unit) s += tag(x + W + unitGap, y + ch / 2 + 4.5, unit);
  s.W = W;
  return s;
}
function drumW(str, cw = 15) { return [...str].reduce((a, c) => a + (c === '.' ? 5 : c === ':' ? 6 : cw), 0); }
// Split-flap characters (alphanumeric values that drums cannot show)
function flapG(x, y, str, { cw = 9, ch = 16, size = 12.5, fill = '#F3EEE2' } = {}) {
  let s = `<rect x="${x - 2}" y="${y - 2}" width="${str.length * cw + 4}" height="${ch + 4}" rx="1.5" fill="#0d0b0a"/>`;
  for (let i = 0; i < str.length; i++) {
    const xx = x + i * cw;
    s += `<rect x="${xx + 0.5}" y="${y}" width="${cw - 1}" height="${ch}" fill="#24211f"/><line x1="${xx + 0.5}" y1="${y + ch / 2}" x2="${xx + cw - 0.5}" y2="${y + ch / 2}" stroke="#000" stroke-width="0.8"/>`;
    if (str[i] !== ' ') s += `<text x="${xx + cw / 2}" y="${y + ch / 2 + size * 0.36}" text-anchor="middle" class="drum" font-size="${size}" fill="${fill}">${esc(str[i])}</text>`;
  }
  return s;
}
// Horizontal edgewise meter with bugs (index markers) and a bracket
function hmeterG(x, y, w, { min = 0, max = 100, value = 50, h = 16, zones = [], bugs = [], bracket = null, major = 5 } = {}) {
  const X = v => x + (Math.max(min, Math.min(max, v)) - min) / (max - min) * w;
  let s = `<rect x="${x - 4}" y="${y - 4}" width="${w + 8}" height="${h + 8}" rx="2.5" fill="${C.bakelite}"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#F2ECDC"/>`;
  for (const z of zones) s += `<rect x="${X(z.from)}" y="${y + h - 5}" width="${X(z.to) - X(z.from)}" height="5" fill="${z.color}"/>`;
  for (let i = 0; i <= major * 2; i++) { const xx = x + w * i / (major * 2); s += `<line x1="${xx}" y1="${y + h}" x2="${xx}" y2="${y + h - (i % 2 ? 4 : 8)}" stroke="${C.ink}" stroke-width="${i % 2 ? 0.7 : 1.2}"/>`; }
  if (bracket) { const a = X(bracket[0]), b = X(bracket[1]); s += `<path d="M${a} ${y + 7}V${y + 2}H${b}V${y + 7}" stroke="${C.ink}" stroke-width="1.6" fill="none"/>`; }
  const px = X(value);
  s += `<g filter="url(#shadow)"><path d="M${px} ${y + h - 1}l-5 -11h10z" fill="${C.orange}" stroke="#1a1614" stroke-width="0.7"/><line x1="${px}" y1="${y + 1}" x2="${px}" y2="${y + h - 9}" stroke="#1a1614" stroke-width="1.3"/></g>`;
  for (const b of bugs) { const bx = X(b.v); s += `<path d="M${bx} ${y + h + 4}l-5 8h10z" fill="${b.color || C.orange}" stroke="#1a1614" stroke-width="0.7"/>`; }
  s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#glassG)"/>`;
  return s;
}
// A small centre-zero needle (turn rate, error)
function cneedleG(cx, cy, r, { value = 0, range = 10, label = '' } = {}) {
  const a = -90 + Math.max(-1, Math.min(1, value / range)) * 70;
  let s = `<circle cx="${cx}" cy="${cy}" r="${r + 4}" fill="${C.bakelite}"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="#F2ECDC"/>`;
  for (let k = -2; k <= 2; k++) { const aa = (-90 + k * 35) * Math.PI / 180; s += `<line x1="${cx + r * Math.cos(aa)}" y1="${cy + r * Math.sin(aa)}" x2="${cx + (r - (k === 0 ? 7 : 4)) * Math.cos(aa)}" y2="${cy + (r - (k === 0 ? 7 : 4)) * Math.sin(aa)}" stroke="${C.ink}" stroke-width="${k === 0 ? 1.6 : 1}"/>`; }
  const ar = a * Math.PI / 180;
  s += `<g filter="url(#shadow)"><line x1="${cx}" y1="${cy}" x2="${cx + (r - 3) * Math.cos(ar)}" y2="${cy + (r - 3) * Math.sin(ar)}" stroke="${C.orange}" stroke-width="1.8" stroke-linecap="round"/><circle cx="${cx}" cy="${cy}" r="3.5" fill="url(#knobG)"/></g>
  <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#glassG)"/>`;
  if (label) s += tag(cx, cy + r + 16, label, { anchor: 'middle' });
  return s;
}
// Round gauge with sweep 240 deg, scale numerals only at the ends and centre
function gaugeG(cx, cy, r, { min = 0, max = 100, value = 50, zones = [], label = '', ends = true, fmt = v => v } = {}) {
  const a0 = 150, a1 = 390, ang = v => a0 + (Math.max(min, Math.min(max, v)) - min) / (max - min) * (a1 - a0);
  const pt = (a, rr) => [cx + rr * Math.cos(a * Math.PI / 180), cy + rr * Math.sin(a * Math.PI / 180)];
  let s = `<circle cx="${cx}" cy="${cy}" r="${r + 5}" fill="${C.bakelite}"/><circle cx="${cx}" cy="${cy}" r="${r}" fill="#F2ECDC"/>`;
  for (const z of zones) { const [x0, y0] = pt(ang(z.from), r - 4), [x1, y1] = pt(ang(z.to), r - 4); s += `<path d="M${x0} ${y0}A${r - 4} ${r - 4} 0 ${(ang(z.to) - ang(z.from)) > 180 ? 1 : 0} 1 ${x1} ${y1}" stroke="${z.color}" stroke-width="5" fill="none"/>`; }
  for (let i = 0; i <= 10; i++) { const [x0, y0] = pt(a0 + i * 24, r), [x1, y1] = pt(a0 + i * 24, r - (i % 5 ? 4 : 8)); s += `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${C.ink}" stroke-width="${i % 5 ? 0.8 : 1.5}"/>`; }
  if (ends) { const [ax, ay] = pt(a0, r - 13), [bx, by] = pt(a1, r - 13); s += tag(ax + 2, ay + 10, fmt(min), { anchor: 'middle' }) + tag(bx - 2, by + 10, fmt(max), { anchor: 'middle' }); }
  const [nx, ny] = pt(ang(value), r - 4), [bx2, by2] = pt(ang(value) + 180, 7);
  s += `<g filter="url(#shadow)"><line x1="${bx2}" y1="${by2}" x2="${nx}" y2="${ny}" stroke="${C.orange}" stroke-width="2" stroke-linecap="round"/><circle cx="${cx}" cy="${cy}" r="4" fill="url(#knobG)"/></g><circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#glassG)"/>`;
  if (label) s += tag(cx, cy + r + 17, label, { anchor: 'middle' });
  return s;
}
// Toggle switch, grid size. Lever up = on.
function toggleG(x, y, { up = true, dark = false, guard = false, guardOpen = false } = {}) {
  let s = '';
  if (guard) s += `<rect x="${x - 12}" y="${y - 20}" width="24" height="40" rx="2.5" fill="${C.alu}" stroke="#6d6a63" stroke-width="0.8"/>`;
  s += `<circle cx="${x}" cy="${y}" r="9" fill="${C.alu}" stroke="#77736b" stroke-width="0.8"/><circle cx="${x}" cy="${y}" r="5.5" fill="#5c5852"/><circle cx="${x}" cy="${y}" r="3.4" fill="#2a2725"/>`;
  const d = up ? -1 : 1;
  s += `<g filter="url(#shadow)"><path d="M${x - 2.2} ${y}L${x - 3.5} ${y + d * 16}A3.5 3.5 0 0 0 ${x + 3.5} ${y + d * 16}L${x + 2.2} ${y}Z" fill="url(#knobG)"/><ellipse cx="${x}" cy="${y + d * 16}" rx="3.5" ry="2.4" fill="#3a3430"/></g>`;
  if (guard) {
    if (guardOpen) s += `<g filter="url(#shadow)"><rect x="${x - 11}" y="${y - 44}" width="22" height="24" rx="3" fill="${C.red}"/><rect x="${x - 11}" y="${y - 23}" width="22" height="4" fill="#7a221b"/></g>`;
    else s += `<g filter="url(#shadow)"><rect x="${x - 11}" y="${y - 19}" width="22" height="38" rx="3" fill="${C.red}" opacity="0.94"/><rect x="${x - 11}" y="${y - 19}" width="22" height="38" rx="3" fill="url(#glassG)"/><path d="M${x - 7} ${y - 13}H${x + 7}" stroke="#7a221b" stroke-width="1.5"/></g>`;
  }
  if (dark) s += `<circle cx="${x}" cy="${y}" r="10" fill="#000" opacity="0.15"/>`;
  return s;
}
// Rocker (two-way, spring centred)
function rockerG(x, y, w = 26, h = 34) {
  return `<rect x="${x - w / 2 - 3}" y="${y - h / 2 - 3}" width="${w + 6}" height="${h + 6}" rx="3" fill="${C.alu}"/>
  <g filter="url(#shadow)"><rect x="${x - w / 2}" y="${y - h / 2}" width="${w}" height="${h}" rx="3" fill="url(#knobG)"/></g>
  <path d="M${x - 5} ${y - h / 4 + 2}l5 -6l5 6M${x - 5} ${y + h / 4 - 2}l5 6l5 -6" stroke="${C.inkLight}" stroke-width="1.5" fill="none"/>
  <line x1="${x - w / 2 + 3}" y1="${y}" x2="${x + w / 2 - 3}" y2="${y}" stroke="#000" stroke-width="1"/>`;
}
// Square push-light with a two-line engraved legend
function pushG(x, y, w, h, lines, { color = 'green', lit = false, flag = false } = {}) {
  const [on, off] = LAMP[color];
  let s = `<rect x="${x - 3}" y="${y - 3}" width="${w + 6}" height="${h + 6}" rx="2.5" fill="${C.alu}"/><rect x="${x - 1.5}" y="${y - 1.5}" width="${w + 3}" height="${h + 3}" rx="2" fill="#4f4c46"/>`;
  if (lit) s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${on}" filter="url(#lampglow)" opacity="0.85"/>`;
  s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="1.5" fill="${lit ? on : off}"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#glassG)"/>`;
  const tc = lit ? '#2a1606' : 'rgba(240,230,210,0.5)';
  lines.forEach((t, i) => { s += `<text x="${x + w / 2}" y="${y + h / 2 + (i - (lines.length - 1) / 2) * 13 + 4.3}" text-anchor="middle" class="legend" font-size="12" fill="${tc}">${esc(t)}</text>`; });
  if (flag) s += `<rect x="${x}" y="${y + h / 2 - 5}" width="${w}" height="10" fill="url(#barber)" opacity="0.95"/>`;
  return s;
}
// Physical soft keys under a tube; labels are drawn on the tube's bottom row by the page itself
function softKeysG(x, y, n, w, { pin = false, pinned = false } = {}) {
  let s = '';
  const kw = (w - (pin ? 30 : 0) - (n - 1) * 6) / n;
  for (let i = 0; i < n; i++) { const kx = x + i * (kw + 6); s += `<g filter="url(#shadow)"><rect x="${kx}" y="${y}" width="${kw}" height="11" rx="2" fill="${C.bakelite}"/><rect x="${kx + 1.5}" y="${y + 1}" width="${kw - 3}" height="6" rx="1.5" fill="#3d3732"/></g>`; }
  if (pin) {
    const px = x + w - 24;
    s += `<rect x="${px - 2}" y="${y - 2}" width="28" height="15" rx="2" fill="${C.alu}"/><g filter="url(#shadow)"><rect x="${px}" y="${y + (pinned ? 2 : 0)}" width="24" height="11" rx="2" fill="${pinned ? C.orange : C.bakelite}"/></g>`;
  }
  return s;
}
// Station board button: lens with station number, name, occupant on a label strip
function stationKeyG(x, y, w, h, { num, name, occ = '', state = 'empty' } = {}) {
  // states: you, crewed, empty, caution, warning, struck
  // dark ship: only the player's own station, cautions and warnings light; crewed and empty differ by the label
  const lens = { you: ['white', true], crewed: ['white', false], empty: ['white', false], caution: ['amber', true], warning: ['red', true], struck: ['white', false] }[state];
  const [on, off] = LAMP[lens[0]];
  const lw = 58;
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="2" fill="#2f2924"/>`;
  s += `<rect x="${x + 2}" y="${y + 2}" width="${lw}" height="${h - 4}" rx="1.5" fill="${C.alu}"/>`;
  if (lens[1]) s += `<rect x="${x + 4}" y="${y + 4}" width="${lw - 4}" height="${h - 8}" fill="${on}" filter="url(#lampglow)" opacity="0.7"/>`;
  s += `<rect x="${x + 4}" y="${y + 4}" width="${lw - 4}" height="${h - 8}" rx="1" fill="${lens[1] ? on : off}"/><rect x="${x + 4}" y="${y + 4}" width="${lw - 4}" height="${h - 8}" fill="url(#glassG)"/>`;
  const tc = lens[1] ? '#241406' : 'rgba(240,230,210,0.55)';
  s += `<text x="${x + 4 + (lw - 4) / 2}" y="${y + h / 2 + 4.3}" text-anchor="middle" class="legend" font-size="12" fill="${tc}">${num} ${esc(name)}</text>`;
  const mark = state === 'caution' ? '▲ ' : state === 'warning' ? '◆ ' : '';
  s += `<text x="${x + lw + 9}" y="${y + h / 2 + 4.3}" class="legend" font-size="12" fill="${state === 'struck' ? '#7d7466' : C.inkLight}">${mark}${esc(occ)}</text>`;
  if (state === 'struck') s += `<rect x="${x + 4}" y="${y + h / 2 - 3}" width="${w - 8}" height="6" fill="url(#barber)" opacity="0.9"/>`;
  return s;
}
// Condition flag: a drum window showing the ship's condition, and the deck's light bar
function condFlagG(x, y, w, h, cond) {
  const col = { GREEN: ['#3f7a3a', '#e8f2dc'], YELLOW: ['#d9a637', '#2a1c06'], RED: ['#b8352b', '#fbe9dc'], DOCKED: ['#2F4A7A', '#e6ecf5'], EMERG: ['#b8352b', '#fbe9dc'] }[cond];
  return `<rect x="${x - 3}" y="${y - 3}" width="${w + 6}" height="${h + 6}" rx="2.5" fill="${C.bakelite}"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${col[0]}"/>
   <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#drumG)" opacity="0.35"/>
   <text x="${x + w / 2}" y="${y + h / 2 + 4.6}" text-anchor="middle" class="legend" font-size="13" fill="${col[1]}">${cond}</text><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#glassG)"/>`;
}

// ---------- world (outside view) ----------
function spaceG(w, h, { seed: sd = 11, stars = 380, haze = true } = {}) {
  seed = sd;
  let s = `<rect width="${w}" height="${h}" fill="#05060a"/>`;
  if (haze) s += `<radialGradient id="neb${sd}" cx="0.72" cy="0.28" r="0.6"><stop offset="0" stop-color="#1c1712" stop-opacity="0.85"/><stop offset="1" stop-color="#05060a" stop-opacity="0"/></radialGradient><rect width="${w}" height="${h}" fill="url(#neb${sd})"/>`;
  for (let i = 0; i < stars; i++) { const x = rnd() * w, y = rnd() * h, r = rnd() < 0.93 ? rnd() * 0.7 + 0.25 : rnd() * 1.1 + 0.6; s += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r.toFixed(2)}" fill="#f4ecdc" opacity="${(0.25 + rnd() * 0.7).toFixed(2)}"/>`; }
  return s;
}
function planetG(cx, cy, r) {
  return `<radialGradient id="pl${cx}" cx="0.55" cy="0.35" r="0.7"><stop offset="0" stop-color="#3b5a6e"/><stop offset="0.7" stop-color="#1d3140"/><stop offset="1" stop-color="#0b141c"/></radialGradient>
  <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#pl${cx})"/><circle cx="${cx}" cy="${cy}" r="${r + 2}" fill="none" stroke="#8fb3c9" stroke-opacity="0.35" stroke-width="5" filter="url(#glow)"/>`;
}
function hullG(x, y, rot = 0, k = 0.7) {
  return `<g transform="translate(${x},${y}) rotate(${rot}) scale(${k})">
    <path d="M0 -70 L14 -52 L16 20 L22 34 L22 58 L-22 58 L-22 34 L-16 20 L-14 -52 Z" fill="#5f625f" stroke="#2b2d2c" stroke-width="2"/>
    <rect x="-10" y="-40" width="20" height="18" fill="#474a48"/><rect x="-12" y="-10" width="24" height="26" fill="#4d504e"/>
    <rect x="-18" y="38" width="12" height="16" fill="#3a3c3b"/><rect x="6" y="38" width="12" height="16" fill="#3a3c3b"/>
    <path d="M-14 -52 L0 -70 L14 -52" fill="none" stroke="#8a8d8a" stroke-width="1.2"/></g>`;
}
function stationG(x, y, k = 1) {
  return `<g transform="translate(${x},${y}) scale(${k})"><circle r="34" fill="none" stroke="#6b6e6a" stroke-width="7"/><circle r="34" fill="none" stroke="#3b3d3b" stroke-width="1.2"/>
   <rect x="-6" y="-34" width="12" height="68" fill="#575a57"/><rect x="-34" y="-6" width="68" height="12" fill="#575a57"/><circle r="11" fill="#6f726e" stroke="#2b2d2c" stroke-width="1.5"/>
   <rect x="-4" y="-48" width="8" height="14" fill="#7a7d79"/></g>`;
}
// Reflector-sight symbology: amber unless told otherwise
const WS = { a: PH.amber, d: PH.dim, r: PH.red };
function wsym(kind, x, y, s = 7, { col = WS.a, fill = false, w = 1.5 } = {}) {
  return `<g filter="url(#glow)">${sym(kind, x, y, s, col, { fill, w })}</g>`;
}
function tgtBracketG(x, y, s = 22, col = WS.a) { return `<g filter="url(#glow)">${sym('bracket', x, y, s, col, { w: 1.8 })}</g>`; }
function navBracketG(x, y, s = 22, col = WS.a) {
  return `<g filter="url(#glow)" stroke="${col}" stroke-width="1.6" fill="none"><path d="M${x - s + 6} ${y - s}H${x - s}V${y + s}H${x - s + 6}M${x + s - 6} ${y - s}H${x + s}V${y + s}H${x + s - 6}"/><path d="M${x - 4} ${y + s + 4}l4 5l4 -5" /></g>`;
}
function wtext(x, y, t, { c = 'amber', size = 13, anchor = 'start', weight = 400 } = {}) { return tt(x, y, t, { c, size, anchor, weight }); }
function progradeG(x, y, col = WS.a) {
  return `<g filter="url(#glow)" stroke="${col}" fill="none" stroke-width="1.5"><circle cx="${x}" cy="${y}" r="9"/><circle cx="${x}" cy="${y}" r="1.6" fill="${col}"/><path d="M${x - 17} ${y}H${x - 11}M${x + 11} ${y}H${x + 17}M${x} ${y - 17}V${y - 11}"/></g>`;
}
function retrogradeG(x, y, col = WS.d) {
  return `<g filter="url(#glow)" stroke="${col}" fill="none" stroke-width="1.4"><circle cx="${x}" cy="${y}" r="9"/><path d="M${x - 6} ${y - 6}l12 12M${x + 6} ${y - 6}l-12 12M${x - 17} ${y}H${x - 11}M${x + 11} ${y}H${x + 17}M${x} ${y + 11}V${y + 17}"/></g>`;
}
function burnG(x, y, rot, label, col = WS.a) {
  return `<g filter="url(#glow)"><g transform="translate(${x},${y}) rotate(${rot})" stroke="${col}" stroke-width="1.6" fill="none"><path d="M0 -12L10 6H-10Z"/><path d="M0 6V18"/></g></g>${wtext(x + 16, y + 5, label, { size: 13 })}`;
}
function leadG(x, y, col = WS.a) {
  return `<g filter="url(#glow)" stroke="${col}" stroke-width="1.4" fill="none"><circle cx="${x}" cy="${y}" r="6"/><path d="M${x} ${y - 10}V${y - 14}M${x} ${y + 10}V${y + 14}M${x - 10} ${y}H${x - 14}M${x + 10} ${y}H${x + 14}"/></g>`;
}
function impactG(x, y, col = WS.r) {
  return `<g filter="url(#glow)" stroke="${col}" stroke-width="2" fill="none"><path d="M${x - 9} ${y - 9}l18 18M${x + 9} ${y - 9}l-18 18"/><circle cx="${x}" cy="${y}" r="13" stroke-dasharray="3 3"/></g>`;
}
function threatG(d, head, col = WS.r) {
  // d: path; head: [x,y,angleDeg] of the missile
  const [hx, hy, ha] = head;
  return `<g filter="url(#glow)"><path d="${d}" stroke="${col}" stroke-width="1.6" fill="none" stroke-dasharray="9 7"/>
   <g transform="translate(${hx},${hy}) rotate(${ha})" stroke="${col}" stroke-width="1.8" fill="none"><path d="M-7 0H7"/><path d="M-1 -5L-8 0L-1 5"/></g></g>`;
}
function edgeG(x, y, ang, lines, { col = 'amber' } = {}) {
  const c = PH[col];
  let s = `<g filter="url(#glow)"><g transform="translate(${x},${y}) rotate(${ang})"><path d="M0 0L-12 -7L-9 0L-12 7Z" fill="${c}"/></g></g>`;
  lines.forEach((t, i) => { s += wtext(x + (ang > 90 || ang < -90 ? 14 : -14), y + 4 + i * 15, t, { c: col, anchor: ang > 90 || ang < -90 ? 'start' : 'end' }); });
  return s;
}

// ---------- grid kit: composite pieces shared by the variations ----------
// The seated deck: chassis band flush with the bottom edge, lip, identity stripe, and the
// condition light bar under the lip (lit only in Yellow and Red).
function deckBandG({ y = 616, w = 1280, h = 184, light = null } = {}) {
  let s = `<g filter="url(#deckshadow)"><rect x="0" y="${y}" width="${w}" height="${h}" fill="url(#chassisG)"/></g>
  <rect x="0" y="${y}" width="${w}" height="6" fill="url(#lipG)"/><rect x="0" y="${y + 6}" width="${w}" height="3" fill="${C.orange}"/>`;
  if (light) {
    const c = light === 'red' ? LAMP.red[0] : LAMP.amber[0];
    s += `<rect x="0" y="${y - 3}" width="${w}" height="3" fill="${c}" filter="url(#lampglow)" opacity="0.8"/><rect x="0" y="${y - 2}" width="${w}" height="2" fill="${c}"/>`;
  }
  return s;
}
// A multi-function display: plate, bezel, tube, physical soft keys and a pin latch.
// draw(tx, ty, tw, th) returns the page content. Rows are 15 units apart, baseline ty+13.
function mfdG(x, y, { draw = () => '', keys = 3, pinned = false, dead = false, w = 288, h = 144 } = {}) {
  let s = plateG(x, y, w, h, { screws: true });
  const tx = x + 12, ty = y + 8, tw = w - 24, th = h - 34;
  const t = tubeG(tx, ty, tw, th, { dead });
  s += t.under + (dead ? '' : draw(tx, ty, tw, th)) + t.over;
  s += softKeysG(tx, y + h - 17, keys, tw, { pin: true, pinned });
  return s;
}
// Soft key labels on a tube's bottom row, centred over the physical keys
function softLabelsG(tx, ty, tw, th, labels, active = 0, pinned = false) {
  const n = labels.length, kw = (tw - 30 - (n - 1) * 6) / n, y = ty + th - 6;
  let s = '';
  labels.forEach((l, i) => { const cx = tx + i * (kw + 6) + kw / 2; s += tt(cx, y, l, { anchor: 'middle', rev: i === active, w: kw - 10, size: 12.5 }); });
  s += tt(tx + tw - 12, y, 'PIN', { anchor: 'middle', size: 12, c: pinned ? 'amber' : 'dim', rev: pinned, w: 26 });
  return s;
}
// Overhead annunciator: hangs from the top edge, centred; only down while something is lit.
// tiles: [{tag, val, sev:'warning'|'caution', ack}] ; mw, mc: master lamps lit
function overheadG(cx, tiles, { mw = false, mc = false, y = 0, w = 640, h = 42, overflow = 0, per = 6 } = {}) {
  const x = cx - w / 2;
  let s = `<rect x="${x + 40}" y="${y}" width="6" height="6" fill="${C.alu}"/><rect x="${x + w - 46}" y="${y}" width="6" height="6" fill="${C.alu}"/>`;
  s += `<g filter="url(#shadow)"><rect x="${x}" y="${y + 2}" width="${w}" height="${h}" rx="4" fill="url(#chassisG)"/></g><rect x="${x}" y="${y + 2}" width="${w}" height="${h}" rx="4" fill="url(#glassG)"/>
  <rect x="${x}" y="${y + h - 1}" width="${w}" height="3" rx="1" fill="${C.orange}"/>`;
  s += pushG(x + 6, y + 7, 52, h - 13, ['MASTER', 'WARNING'], { color: 'red', lit: mw });
  s += pushG(x + w - 58, y + 7, 52, h - 13, ['MASTER', 'CAUTION'], { color: 'amber', lit: mc });
  const tx0 = x + 66, tw = (w - 132 - (per - 1) * 6) / per;
  tiles.slice(0, per).forEach((t, i) => {
    const xx = tx0 + i * (tw + 6), color = t.sev === 'warning' ? 'red' : 'amber';
    const [on] = LAMP[color];
    s += `<rect x="${xx}" y="${y + 6}" width="${tw}" height="${h - 11}" rx="2" fill="#0c0a09"/>`;
    s += `<rect x="${xx + 2}" y="${y + 8}" width="${tw - 4}" height="16" fill="${on}" ${t.ack ? '' : 'filter="url(#lampglow)"'} opacity="${t.ack ? 0.85 : 1}"/><rect x="${xx + 2}" y="${y + 8}" width="${tw - 4}" height="16" fill="url(#glassG)"/>`;
    s += `<text x="${xx + tw / 2}" y="${y + 20.5}" text-anchor="middle" class="legend" font-size="12" fill="#241406">${t.sev === 'warning' ? '◆ ' : '▲ '}${esc(t.tag)}</text>`;
    if (t.val) { const cw = 8.6, fw = t.val.length * cw; s += flapG(xx + (tw - fw) / 2, y + 26, t.val, { cw, ch: 13, size: 12 }); }
  });
  if (overflow) s += drumG(x + w - 96, y + 26, '+' + overflow, { cw: 10, ch: 12, size: 11 });
  return s;
}
// Intercom repeater: the callout ticker, a strip tube on an arm from the left edge.
// lines: [{tag, who, text, sev}] newest last
function tickerG(lines, { x = 16, y = 54, w = 520, h = 66, hang = true } = {}) {
  // hung from the overhead on two rods (or from the top edge when the annunciator is up)
  let s = hang ? `<rect x="${x + 60}" y="0" width="4" height="${y + 4}" fill="${C.alu}"/><rect x="${x + w - 64}" y="0" width="4" height="${y + 4}" fill="${C.alu}"/>` : '';
  const t = tubeG(x + 6, y + 5, w - 12, h - 10, { r: 5, bezel: 5 });
  s += `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="7" fill="${C.bakelite}"/></g>` + t.under;
  lines.forEach((l, i) => {
    const yy = y + 21 + i * 18, mk = l.sev === 'warning' ? '◆' : l.sev === 'caution' ? '▲' : ' ';
    s += tt(x + 14, yy, mk, { c: l.sev === 'warning' ? 'red' : 'amber', size: 13 });
    s += tt(x + 30, yy, l.tag.padEnd(5), { rev: true, w: 42, size: 12.5 });
    s += tt(x + 78, yy, (l.who ? l.who + ': ' : '') + l.text, { size: 13, c: l.old ? 'dim' : 'amber' });
  });
  return s + t.over;
}
// A console's nameplate in the deck view: the prompt, with key caps; and the response slot
function promptPlateG(x, y, name, verbs, { response = null } = {}) {
  const w = 34 + name.length * 7 + verbs.reduce((a, v) => a + 24 + v[1].length * 6.6, 0);
  let s = plateG(x - w / 2, y, w, 26, { screws: false });
  s += tag(x - w / 2 + 10, y + 17.5, name, { size: 13 });
  let xx = x - w / 2 + 22 + name.length * 7.4;
  for (const [k, v] of verbs) { s += keyG(xx, y + 5.5, k, { anchor: 'start' }); xx += 19 + k.length * 5; s += tag(xx, y + 17.5, v); xx += 10 + v.length * 6.6; }
  if (response) {
    const rw = response.length * 7.8 + 20;
    const t = tubeG(x - rw / 2, y + 34, rw, 20, { r: 3, bezel: 4 });
    s += t.under + tt(x, y + 48, response, { anchor: 'middle', size: 13 }) + t.over;
  }
  return s;
}

// A pod: chassis housing with lip and identity stripe, for a deck unit that stands on its own
function podG(x, y, w, h, { stripe = true, r = 5 } = {}) {
  return `<g filter="url(#deckshadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="url(#chassisG)"/></g>
  <rect x="${x}" y="${y}" width="${w}" height="5" rx="2" fill="url(#lipG)"/>${stripe ? `<rect x="${x}" y="${y + 5}" width="${w}" height="2.5" fill="${C.orange}"/>` : ''}`;
}
// Soft-key rail: keys whose faces carry split-flap legends (they name the pages of the station on
// show, so they must change), then a PIN latch and a FOLD key. Used raised and folded.
function softRailG(x, y, w, labels, { active = 0, pinned = false, folded = false, h = 24 } = {}) {
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="3" fill="${C.chassisDeep}"/>`;
  const extra = 58, n = labels.length, kw = (w - extra - 8 - (n - 1) * 5) / n;
  labels.forEach((l, i) => {
    const kx = x + 4 + i * (kw + 5), on = i === active && !folded;
    s += `<g filter="url(#shadow)"><rect x="${kx}" y="${y + 3 + (on ? 1.5 : 0)}" width="${kw}" height="${h - 6}" rx="2.5" fill="${on ? '#3e3732' : C.bakelite}"/></g>`;
    const fw = l.length * 8.4;
    s += flapG(kx + (kw - fw) / 2, y + 6.5 + (on ? 1.5 : 0), l, { cw: 8.4, ch: 11.5, size: 11.5, fill: on ? PH.amber : '#F3EEE2' });
  });
  const px = x + w - extra + 2;
  s += `<rect x="${px - 1}" y="${y + 2}" width="26" height="${h - 4}" rx="2" fill="${C.alu}"/><g filter="url(#shadow)"><rect x="${px + 1}" y="${y + 4 + (pinned ? 2 : 0)}" width="22" height="${h - 9}" rx="2" fill="${pinned ? C.orange : C.bakelite}"/></g>`;
  s += `<text x="${px + 12}" y="${y + h / 2 + 4 + (pinned ? 2 : 0)}" text-anchor="middle" class="legend" font-size="12" fill="${pinned ? '#2a1606' : C.inkLight}">PIN</text>`;
  const fx = px + 29;
  s += `<g filter="url(#shadow)"><rect x="${fx}" y="${y + 3}" width="24" height="${h - 6}" rx="2.5" fill="${C.bakelite}"/></g>
    <path d="${folded ? `M${fx + 7} ${y + h / 2 + 3}l5 -6l5 6` : `M${fx + 7} ${y + h / 2 - 3}l5 6l5 -6`}" stroke="${C.inkLight}" stroke-width="1.8" fill="none"/>`;
  return s;
}
// Strip tube with lines of phosphor text
function stripG(x, y, w, h, lines, { size = 13, lh = 17, pad = 8, dead = false } = {}) {
  const t = tubeG(x, y, w, h, { r: 4, bezel: 5, dead });
  let s = t.under;
  if (!dead) lines.forEach((l, i) => { if (l) s += tt(x + pad, y + pad + size - 2 + i * lh, l.t, { c: l.c || 'amber', size }); });
  return s + t.over;
}
