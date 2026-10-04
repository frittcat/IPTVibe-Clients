import {mkdir} from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';

const source=path.resolve('assets/iptvibe-icon.png');
const mobile=path.resolve('..','mobile','app','src','main','res');
const sizes={mdpi:48,hdpi:72,xhdpi:96,xxhdpi:144,xxxhdpi:192};

for(const [density,size] of Object.entries(sizes)){
  const dir=path.join(mobile,'mipmap-'+density);
  await mkdir(dir,{recursive:true});
  await sharp(source).resize(size,size).png().toFile(path.join(dir,'ic_launcher.png'));
  await sharp(source).resize(size,size).png().toFile(path.join(dir,'ic_launcher_round.png'));
}
console.log('IPTVibe brand assets synchronized from official master icon.');
