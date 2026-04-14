#!/usr/bin/env python3
"""
MCP Server Stress Test
Tests the metatron MCP server with various queries to check for:
- Correct results
- Server stability
- Error handling
- Performance issues
"""

import json
import asyncio
import websockets
import time
from typing import Dict, List, Tuple

# Test queries organized by category
TEST_QUERIES = {
    "basic_arithmetic": [
        "1+2",
        "10-5",
        "3*4",
        "2.5+3.7",
        "100.as(real::T).minus(99.5)",
        "1.plus(2).mult(3)",
        "10.minus(5).plus(3)",
    ],

    "lists": [
        "[1,2,3,4,5]",
        "[1,2,3]+[4,5,6]",
        "[[1,2],[3,4],[5,6]]",
        "[1,2,3,4,5]>-.sum()",
        "[1,2,3,4,5]>-.mult(2)",
        "[10,20,30]>-.plus(5)",
        "[1,2,3,4]>-.count()",
        "[[1,2,3],[4,5,6]]>-.>-.sum()",
        "[1,2,3]>>0",
        "[1,2,3]>>1",
        "[1,2,3]>>2",
    ],

    "records": [
        "[a=>1,b=>2,c=>3]",
        "[name=>'marko',age=>29]",
        "[a=>2,b=>[c=>d]]",
        "[a=>[b=>[c=>[d=>e]]]]",
        "[x=>1,y=>2,z=>3]>-.values()",
    ],

    "streams": [
        "[1,2,3]>-",
        "[1,2,3]>-.mult(2)>-.sum()",
        "[1,2,3,4,5]>-.filter(?>2)",
        "[1,2,3,4,5]>-.map(mult(2))",
    ],

    "branching": [
        "1-<[?=1 => 'one', _ => 'other']",
        "5-<[?<3 => 'small', ?<10 => 'medium', _ => 'large']",
        "[1,2,3,4,5]>-.map(-<[?=3 => 'three', _ => _])",
        "10-<[?=10 => 'ten', ?=20 => 'twenty', _ => 'unknown']",
    ],

    "pattern_matching": [
        "1-<[?=1 => 'agh', _ => 43445]",
        "[1,2,3,4,5]>-.map(-<[?=1=>100,?=2=>200,_=>_])",
        "42-<[?<50 => 'pass', _ => 'fail']",
    ],

    "complex_expressions": [
        "[1,2,3]+[4,7]>-.sum()-<[_=>_]==[?=17=>+24]",
        "[1,2,3,4]+[2,34]",
        "1-<[?=1 => 'agh', _ => 43445]",
        "[a=>4,b=>3]==[_=>plus(34)]",
        "[1,2,3]>-.mult(2)>-.plus(10)>-.sum()",
    ],

    "data_filtering": [
        "[name=>marko,locations=>[home=>[city=>'santa fe'],bunker=>[city=>'waldport']]].where[locations=>[home=>[city=>'santa fe']]].select[name=>_]",
        "[name=>marko,age=>29].select[name=>_]",
        "[a=>1,b=>2,c=>3].select[a=>_,c=>_]",
        "[x=>10,y=>20,z=>30].where[x=>10]",
        "[person=>[name=>'alice',age=>30]].select[person=>[name=>_]]",
    ],

    "type_conversions": [
        "[1,2,3,4].as(rec::T)",
        "1.as(str::T)",
        "'123'.as(int::T)",
        "true.as(str::T)",
    ],

    "strings": [
        "'hello world'",
        '"hello world"',
        "'''hello world'''",
        '"""hello world"""',
        "'hello' + ' ' + 'world'",
        "'metatron'",
        "'test string with spaces'",
        '"""hello""".plus(123.as(str::T))',
        "'number: '.plus(42.as(str::T))",
    ],

    "booleans": [
        "true",
        "false",
        "1.eq(1)",
        "5.gt(3)",
        "10.lt(5)",
    ],

    "edge_cases": [
        "[,]",
        "[=>]",
        "0",
        "-1",
        "0.0",
        "''",
    ],

    "nested_structures": [
        "[[1,2],[3,4],[5,6]]>-._/sum()\\_",
        "[a=>[b=>1,c=>2],d=>[e=>3,f=>4]]",
        "[a=>[b=>1,c=>2],d=>[e=>3,f=>4]]>>a",
        "[a=>[b=>1,c=>2],d=>[e=>3,f=>4]]>>a>>b",
        "[x=>[y=>[z=>42]]]",
        "[[[[1]]]]",
    ],

    "error_handling": [
        "[1,2,3]>>10",  # Out of bounds access (returns noobj)
        "nonexistent_var",  # Undefined variable (returns as-is)
        "'hello'.plus(123)",  # Type mismatch (should return fail)
        "[a=>1,b=>2]>>c",  # Missing key (returns noobj)
    ],

    "fail_objects": [
        "'abc'.as(int::T)",  # Invalid string to int conversion - should return fail
        "'not a number'.as(real::T)",  # Invalid string to real conversion
        "[1,2,3].at(100)",  # Out of bounds - returns noobj, not fail
        "[x=>1,y=>2,z=>3]>-.values()",  # Type mismatch - returns fail
    ],

    "mystery_queries": [
        "1.^.id().>><+/>",  # What does this do? :D
        "1.^",
        "1.id()",
        "[a=>1,b=>2].^",
    ],
}

class MCPTester:
    def __init__(self, uri: str = "ws://localhost:8999"):
        self.uri = uri
        self.request_id = 0
        self.results: List[Dict] = []

    def next_id(self) -> int:
        self.request_id += 1
        return self.request_id

    async def send_eval(self, ws, code: str) -> Tuple[str, Dict, float]:
        """Send an eval request and return (code, result, duration)"""
        request = {
            "jsonrpc": "2.0",
            "id": self.next_id(),
            "method": "tools/call",
            "params": {
                "name": "eval",
                "arguments": {
                    "code": code
                }
            }
        }

        start_time = time.time()
        await ws.send(json.dumps(request))
        response_str = await ws.recv()
        duration = time.time() - start_time

        response = json.loads(response_str)
        return code, response, duration

    async def run_test_category(self, ws, category: str, queries: List[str]):
        """Run all queries in a category"""
        print(f"\n{'='*80}")
        print(f"Testing: {category.replace('_', ' ').title()}")
        print(f"{'='*80}")

        for query in queries:
            try:
                code, response, duration = await self.send_eval(ws, query)

                # Extract result
                if "result" in response:
                    content = response["result"].get("content", [])
                    if content and len(content) > 0:
                        text = content[0].get("text", "")
                        is_error = response["result"].get("isError", False)

                        status = "❌ ERROR" if is_error else "✅ OK"
                        print(f"{status} [{duration*1000:.1f}ms] {query}")
                        print(f"    → {text[:100]}")

                        self.results.append({
                            "category": category,
                            "query": query,
                            "result": text,
                            "duration": duration,
                            "error": is_error
                        })
                elif "error" in response:
                    print(f"❌ RPC ERROR [{duration*1000:.1f}ms] {query}")
                    print(f"    → {response['error'].get('message', 'Unknown error')}")

                    self.results.append({
                        "category": category,
                        "query": query,
                        "result": response["error"].get("message", ""),
                        "duration": duration,
                        "error": True
                    })

                # Small delay between requests
                await asyncio.sleep(0.1)

            except Exception as e:
                print(f"❌ EXCEPTION [{query}]: {e}")
                self.results.append({
                    "category": category,
                    "query": query,
                    "result": str(e),
                    "duration": 0,
                    "error": True
                })

    async def run_all_tests(self):
        """Run all test categories"""
        print(f"\n{'#'*80}")
        print(f"# MCP Server Stress Test")
        print(f"# Connecting to: {self.uri}")
        print(f"# Total queries: {sum(len(queries) for queries in TEST_QUERIES.values())}")
        print(f"{'#'*80}")

        try:
            async with websockets.connect(self.uri) as ws:
                print(f"\n✅ Connected to MCP server")

                # Run each category
                for category, queries in TEST_QUERIES.items():
                    await self.run_test_category(ws, category, queries)

                # Print summary
                self.print_summary()

        except Exception as e:
            print(f"\n❌ Failed to connect to server: {e}")
            print(f"Make sure the metatron server is running on {self.uri}")

    def print_summary(self):
        """Print test summary statistics"""
        print(f"\n{'='*80}")
        print(f"Test Summary")
        print(f"{'='*80}")

        total = len(self.results)
        errors = sum(1 for r in self.results if r["error"])
        success = total - errors

        total_time = sum(r["duration"] for r in self.results)
        avg_time = total_time / total if total > 0 else 0
        max_time = max((r["duration"] for r in self.results), default=0)
        min_time = min((r["duration"] for r in self.results), default=0)

        print(f"Total queries:    {total}")
        print(f"Successful:       {success} ({success/total*100:.1f}%)")
        print(f"Errors:           {errors} ({errors/total*100:.1f}%)")
        print(f"\nTiming:")
        print(f"Total time:       {total_time:.2f}s")
        print(f"Average time:     {avg_time*1000:.1f}ms")
        print(f"Min time:         {min_time*1000:.1f}ms")
        print(f"Max time:         {max_time*1000:.1f}ms")

        # Category breakdown
        print(f"\nBy Category:")
        for category in TEST_QUERIES.keys():
            cat_results = [r for r in self.results if r["category"] == category]
            cat_errors = sum(1 for r in cat_results if r["error"])
            cat_success = len(cat_results) - cat_errors
            print(f"  {category:25s} {cat_success}/{len(cat_results)} passed")

        # Slowest queries
        print(f"\nSlowest Queries:")
        slowest = sorted(self.results, key=lambda r: r["duration"], reverse=True)[:5]
        for r in slowest:
            print(f"  {r['duration']*1000:6.1f}ms - {r['query'][:60]}")

async def main():
    tester = MCPTester()
    await tester.run_all_tests()

if __name__ == "__main__":
    asyncio.run(main())
