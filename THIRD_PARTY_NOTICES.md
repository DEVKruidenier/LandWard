# Third-party notices

## SQLite JDBC

- Source: https://github.com/xerial/sqlite-jdbc
- Bundled version: 3.54.1.0, with the full native platform resource set.
- License: Apache-2.0 and the upstream SQLite notices; bundled at
  `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and `LICENSE.zentus`.

## MySQL Connector/J

- Source: https://github.com/mysql/mysql-connector-j
- Bundled version: 9.3.0.
- License: GPL-2.0 with the Universal FOSS Exception; upstream terms are in
  the bundled root `LICENSE` file.
- LandWard uses the classic JDBC protocol. The separate, unused X DevAPI and
  its Protocol Buffers implementation are not part of the distribution.
- JDBC authentication, TLS, character encodings and connection handling are retained.

## HikariCP

- Source: https://github.com/brettwooldridge/HikariCP
- Bundled version: 6.3.0, relocated under LandWard's private library namespace.
- License: Apache-2.0. See https://github.com/brettwooldridge/HikariCP/blob/dev/LICENSE.

## Adventure

- Source: https://github.com/KyoriPowered/adventure
- Modules: Adventure API, Adventure Key, Adventure Plain Text Serializer and their Kyori runtime dependencies.
- Bundled API and serializer version: 5.2.0.
- License: MIT. See https://github.com/KyoriPowered/adventure/blob/main/license.txt.
- Required for LandWard's text and GUI code on Spigot, which does not provide Adventure itself.

LandWard bundles and relocates the bStats Bukkit metrics client so server owners do
not need to install a separate metrics plugin.

## bStats

- Project: https://bstats.org/
- Source: https://github.com/Bastian/bstats-metrics
- License: MIT
- Bundled version: 3.2.1

Copyright (c) 2017 Bastian Oppermann

Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to use, copy,
modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to the
following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
