const fs = require('node:fs');
const assert = require('node:assert/strict');

const project = 'https://api.modrinth.com/v2/project/landward';
const source = 'https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/';
const replacements = new Map([
  ['Create your first land', 'landward-main-menu.gif'],
  ['Player claim map', 'landward-chunk-map.gif'],
  ['Player claim settings', 'landward-access-members.gif'],
  ['Administration and Safe Zones', 'landward-admin-main-menu.gif'],
  ['Safe Zone settings', 'landward-admin-settings.gif'],
  ['Active worlds', 'landward-active-worlds.gif'],
]);
const label = 'Captured in 1.0.6.0-beta9.';
const authorization = process.env.MODRINTH_TOKEN;

async function call(method, url, body) {
  const headers = { Authorization: authorization };
  if (body) headers['Content-Type'] = 'application/octet-stream';
  const response = await fetch(url, { method, headers, body, redirect: 'error' });
  if (!response.ok) throw new Error(`Modrinth ${method} returned ${response.status}`);
  return response;
}

async function read() {
  return (await call('GET', project)).json();
}

function galleryUrl(fields) {
  return `${project}/gallery?${new URLSearchParams(fields)}`;
}

async function verifyGif(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Image fetch returned ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  assert(bytes.length < 4_900_000 && /^GIF8[79]a$/.test(bytes.subarray(0, 6).toString()),
    'Expected a GIF smaller than 4,900,000 bytes');
  return bytes;
}

async function main() {
  assert(authorization, 'Missing publishing configuration');
  const before = await read();
  const normalize = (body) => body.replace(/\r\n/g, '\n').trimEnd();
  assert(normalize(before.body) === normalize(fs.readFileSync('.github/modrinth/description.md', 'utf8')),
    'The description must be synchronized before replacing its gallery');
  const plan = [];
  for (const [title, file] of replacements) {
    const found = before.gallery.filter((item) => item.title === title && /\.gif(?:[?#]|$)/i.test(item.url));
    assert(found.length === 1 && !found[0].description?.includes(label), `Unexpected existing entry: ${title}`);
    const [old] = found;
    const bytes = await verifyGif(source + file);
    plan.push({ old, bytes, description: [old.description, label].filter(Boolean).join('\n\n') });
  }

  // Keep all original items until each replacement is present and readable.
  const newUrls = new Set();
  try {
    for (const item of plan) {
      await call('POST', galleryUrl({ ext: 'gif', featured: false, title: item.old.title,
        description: item.description, ordering: item.old.ordering }), item.bytes);
      const live = await read();
      const replacement = live.gallery.find((entry) => entry.title === item.old.title
        && entry.description === item.description && entry.url !== item.old.url);
      assert(replacement, `Uploaded image could not be identified: ${item.old.title}`);
      item.newUrl = replacement.url;
      newUrls.add(item.newUrl);
      assert(new URL(item.newUrl).hostname === 'cdn.modrinth.com', 'Unexpected gallery media host');
      await verifyGif(replacement.raw_url || replacement.url);
      console.log(`Verified uploaded image: ${item.old.title}`);
    }
  } catch (error) {
    for (const url of newUrls) await call('DELETE', galleryUrl({ url }));
    throw error;
  }

  for (const { old, newUrl } of plan) {
    await call('DELETE', galleryUrl({ url: old.url }));
    if (old.featured) await call('PATCH', galleryUrl({ url: newUrl, featured: true }));
  }

  const after = await read();
  assert(after.status === before.status && after.body === before.body
    && JSON.stringify(after.versions) === JSON.stringify(before.versions), 'Unrelated project metadata changed');
  assert(after.gallery.length === before.gallery.length, 'Unexpected final gallery size');
  for (const { old, newUrl, description } of plan) {
    assert(!after.gallery.some((item) => item.url === old.url), `Old image still present: ${old.title}`);
    const item = after.gallery.find((image) => image.url === newUrl);
    assert(item && item.title === old.title && item.description === description
      && item.ordering === old.ordering && item.featured === old.featured, `Replacement differs: ${old.title}`);
  }
  for (const original of before.gallery.filter((item) => !plan.some(({ old }) => old.url === item.url))) {
    assert.deepEqual(after.gallery.find((item) => item.url === original.url), original);
  }
  console.log(`Verified ${plan.length} gallery replacements; project status: ${after.status}`);
}

main().catch((error) => { console.error(error.message); process.exitCode = 1; });
