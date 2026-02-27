#!/usr/bin/env python3
"""
Simple mtron executor via WebSocket
Usage: ./mtron-exec.py "1.plus(2)"
"""
import asyncio
import websockets
import sys

async def execute(code: str, host: str = 'ws://localhost:8999'):
    try:
        async with websockets.connect(host) as ws:
            await ws.send(code)
            result = await ws.recv()
            return result
    except Exception as e:
        return f"Error: {e}"

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: mtron-exec.py '<mtron code>'")
        print("Example: mtron-exec.py '1.plus(2)'")
        sys.exit(1)

    code = sys.argv[1]
    result = asyncio.run(execute(code))
    print(result)
