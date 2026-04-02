#!/usr/bin/env python3
"""Test script to see what doc_json() returns from metatron."""

import asyncio
import websockets

async def test_doc_json():
    async with websockets.connect("ws://127.0.0.1:8999") as ws:
        # Test doc_json with the correct format: "query"./m/web/inst/doc_json()
        test_cases = [
            ("mean", '"*mean?docq"./m/web/inst/doc_json()'),
            ("chat", '"*/m/llm/inst/chat?docq"./m/web/inst/doc_json()'),
            ("stack", '"*/m/mach/inst/stack?docq"./m/web/inst/doc_json()'),
        ]

        for name, query in test_cases:
            print(f"\n{'='*60}")
            print(f"Testing {name}")
            print(f"Query: {query}")
            await ws.send(query)
            result = await ws.recv()
            result_str = result.decode('utf-8') if isinstance(result, bytes) else result
            print(f"Result:\n{result_str}")
            print(f"Length: {len(result_str)}")

if __name__ == "__main__":
    asyncio.run(test_doc_json())
