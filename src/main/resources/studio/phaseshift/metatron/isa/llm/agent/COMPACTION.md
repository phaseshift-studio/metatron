# Compaction Instruction

The `compaction()` instruction is used to clear your memory of suplupherous information so as to reduce the amount of data you consume at the beginning of each interaction. To do this, compaction does the following:

    1. Goes through your memory and locates important concepts that should be remembered.
    2. Transfers those concepts to a temporary location (e.g. notes).
    3. Clears your memory entirely.
    4. (Optional) Reconstructs your important memories in order to reduce their size without reducing their information content.
    5. Transfers the important concepts from the temporary location back into your memory location.

The default implementation is provided below. This implementation can be overriden as needed via: `/m/llm/agent/inst/compaction -> <your_compaction_instruction>`

```mtron
compaction?memory<=memory(memvid=>vid(),tempvid=>vid() * <../memtemp>){ 
  score().sort().take(n).to(*tempvid);
  memvid/# -> noobj;
  *tempvid.at(memvid); 
}
```
