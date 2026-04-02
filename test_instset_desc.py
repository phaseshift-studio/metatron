#!/usr/bin/env python3
"""Test script to verify instruction set description fetching."""

import asyncio
import websockets

async def test_instset_desc():
    async with websockets.connect("ws://127.0.0.1:8999") as ws:
        # Test fetching /m instruction set description
        test_cases = [
            ("/m", '"*/m?docq>>desc".doc_json()'),
            ("/m/llm", '"*/m/llm?docq>>desc".doc_json()'),
            ("/m/mach", '"*/m/mach?docq>>desc".doc_json()'),
        ]

        for name, query in test_cases:
            print(f"\n{'='*60}")
            print(f"Testing {name} description")
            print(f"Query: {query}")
            await ws.send(query)
            result = await ws.recv()
            result_str = result.decode('utf-8') if isinstance(result, bytes) else result
            print(f"Result:\n{result_str}")

if __name__ == "__main__":
    asyncio.run(test_instset_desc())
