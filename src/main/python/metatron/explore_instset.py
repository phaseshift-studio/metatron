#!/usr/bin/env python3
"""
Simple exploration script to see what metatron returns for instset queries.
"""

#  metatron: a distributed virtual machine and language
#   Copyright (C) 2025- PhaseShift Studio, LLC
# 
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
# 
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.

import asyncio
import websockets

HOST = "ws://127.0.0.1:8999"

async def query(expression: str):
    """Send a query and return raw result."""
    async with websockets.connect(HOST) as ws:
        print(f"\n{'='*60}")
        print(f"QUERY: {expression}")
        print('='*60)
        await ws.send(expression)
        result = await asyncio.wait_for(ws.recv(), timeout=30)
        result_str = result if isinstance(result, str) else result.decode('utf-8')
        print(f"RESULT ({len(result_str)} chars):")
        print(result_str)
        print('='*60)
        return result_str

async def main():
    # Test various queries to see output format
    queries = [
        # === RAW OUTPUT ===
        # Basic instset metadata
        "*/m/mach/",

        # Types in the instset
        "*/m/mach/+/",

        # Instructions
        "*/m/mach/inst/+/",

        # Single type definition
        "*/m/mach/machine/",

        # === PRETTY OUTPUT via /m/web/inst/doc() ===
        # This should give us toCleanString() format
        "'*/m/mach/+/'./m/web/inst/doc()",

        # Single type via doc()
        "'*/m/mach/machine/'./m/web/inst/doc()",

        # Instructions via doc()
        "'*/m/mach/inst/+/'./m/web/inst/doc()",

        # Single instruction via doc()
        "'*/m/mach/inst/run/'./m/web/inst/doc()",
    ]

    for q in queries:
        try:
            await query(q)
        except Exception as e:
            print(f"ERROR for '{q}': {e}")

        # Small delay between queries
        await asyncio.sleep(0.5)

if __name__ == "__main__":
    asyncio.run(main())
