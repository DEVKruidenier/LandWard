import { readFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';

const gifLimit = 4_900_000;
const repository = 'https://github.com/DEVKruidenier/LandWard';
const markdown = await readFile(
    process.argv[2] ?? new URL('./description.md', import.meta.url), 'utf8',
);
const targets = new Map();

// The maintained description uses inline Markdown links and images.
const inlineLink = /(!?)\[([^\]\n]+)\]\(([^\s()]+)\)/g;
const unparsed = markdown.replace(inlineLink, (_, image, label, destination) => {
    const url = new URL(destination);
    if (url.protocol !== 'https:' || url.username || url.password) {
        throw new Error(`Expected a public HTTPS URL for ${label}`);
    }
    targets.set(url.href, { label, image: Boolean(image) });
    return '';
});
if (/!?\[[^\]\n]*\]\s*\(|https?:\/\/|<(?:a|img)\b/i.test(unparsed)) {
    throw new Error('Unparsed link in description. Check the Markdown link syntax.');
}
for (const path of ['', '/issues', '/wiki']) {
    if (!targets.has(repository + path)) {
        targets.set(repository + path, { label: 'Project link', image: false });
    }
}

async function request(url, method) {
    for (let attempt = 0; ; attempt++) {
        try {
            const response = await fetch(url, {
                method,
                headers: { accept: 'text/html,image/*,*/*;q=0.8' },
                signal: AbortSignal.timeout(15_000),
            });
            if (new URL(response.url).protocol !== 'https:') {
                throw new Error(`Redirected outside HTTPS: ${response.url}`);
            }
            if (attempt === 0 && (response.status === 429 || response.status >= 500)) {
                await response.body?.cancel();
            } else {
                return response;
            }
        } catch (error) {
            if (attempt > 0) throw error;
        }
        await delay(1000);
    }
}

function paletteBytes(flags) {
    return flags & 0x80 ? 3 * (2 ** ((flags & 7) + 1)) : 0;
}

function gifFrames(bytes) {
    if (!/^GIF8[79]a$/.test(bytes.toString('ascii', 0, 6)) || bytes.length < 14) {
        throw new Error('Invalid GIF signature');
    }
    let offset = 13 + paletteBytes(bytes[10]);
    let frames = 0;
    while (offset < bytes.length) {
        const block = bytes[offset++];
        if (block === 0x3b && offset === bytes.length) return frames;
        if (block === 0x2c && offset + 9 < bytes.length) {
            offset += 10 + paletteBytes(bytes[offset + 8]);
            frames++;
        } else if (block === 0x21) {
            offset++; // Extension label.
        } else {
            break;
        }
        // Image and extension data are both encoded as size-prefixed sub-blocks.
        while (offset < bytes.length && bytes[offset] !== 0) {
            offset += 1 + bytes[offset];
        }
        offset++;
    }
    throw new Error('Invalid or incomplete GIF blocks');
}

async function check(url, { label, image }) {
    const response = await request(url, 'GET');
    const contentType = response.headers.get('content-type')?.split(';')[0];
    let details = '';
    if (!response.ok) {
        await response.body?.cancel();
        throw new Error(`GET ${response.status} ${response.url}`);
    }
    if (image) {
        const bytes = Buffer.from(await response.arrayBuffer());
        details = `${bytes.length} bytes`;
        if (new URL(url).pathname.toLowerCase().endsWith('.gif')) {
            if (contentType !== 'image/gif' || bytes.length > gifLimit) {
                throw new Error(`Expected image/gif up to ${gifLimit} bytes, got ${contentType}, ${details}`);
            }
            const frames = gifFrames(bytes);
            if (frames < 2) throw new Error('GIF is not animated');
            details += `, ${frames} frames`;
        } else if (
            contentType !== 'image/png'
            || bytes.subarray(0, 16).toString('hex') !== '89504e470d0a1a0a0000000d49484452'
            || bytes.subarray(-12).toString('hex') !== '0000000049454e44ae426082'
        ) {
            throw new Error(`Expected a complete PNG, got ${contentType}`);
        }
    } else {
        await response.body?.cancel();
        if (contentType !== 'text/html') throw new Error(`Expected a web page, got ${contentType}`);
    }
    const head = await request(url, 'HEAD').then(
        (result) => result.status,
        (error) => error.message,
    );
    console.log(`OK ${label} | GET ${response.status} | HEAD ${head} | ${contentType}${details ? ` | ${details}` : ''}`);
    console.log(`   ${url}${response.url === url ? '' : ` → ${response.url}`}`);
}

for (const [url, target] of targets) {
    try {
        await check(url, target);
    } catch (error) {
        console.error(`FAIL ${target.label} | ${url} | ${error.message}`);
        process.exitCode = 1;
    }
}
