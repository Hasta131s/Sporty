const fs = require('fs');
const path = require('path');

try {
    console.log('--- EXTRACTING CLASSPATH JARS ---');
    const binPath = 'app/build/kspCaches/debug/classpath-entries.bin';
    if (fs.existsSync(binPath)) {
        const content = fs.readFileSync(binPath, 'utf-8');
        // Extract lines or paths with .jar or .aar and print them
        const matches = content.match(/[^\s;\n\r]*\.jar[^\s;\n\r]*/g) || [];
        const uniqueJars = [...new Set(matches)];
        console.log('Unique Jars found:', uniqueJars.slice(0, 50));
    } else {
        console.log('classpath-entries.bin not found');
    }
} catch (e) {
    console.error('Extraction failed:', e.message);
}
