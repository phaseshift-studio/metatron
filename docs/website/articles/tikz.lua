-- Two surfaces, one source. Both branches below serve the same article markdown.
--
-- ```tikz FENCED BLOCKS: hand the tikz-cd straight through to pdflatex (the PDF
-- surface) as raw LaTeX. Other outputs keep the fence as source.
--
-- RENDERED FIGURES: the article carries `![figure N](images/<article>/fig-NN.svg)`
-- and the tikz-cd source sits beside the image as fig-NN.tex. The PDF surface reads
-- that sidecar back, so the PDF still gets the *typeset* vector diagram instead of a
-- bitmap — and it gets it as a bare `\begin{equation*}…\end{equation*}`, which is
-- cleaner than the nested `\[ \begin{equation*}…\end{equation*} \]` that the same
-- figure produced while the TeX was still inline in a `$$…$$` span. Every other
-- output (HTML, docx, …) keeps the image.
--
-- Invoke as usual:  pandoc article.md -o article.pdf --lua-filter=tikz.lua
function CodeBlock(block)
  if block.classes[1] == "tikz" then
    return pandoc.RawBlock("latex", block.text)
  end
end

-- The directory of the article being converted, so a sidecar resolves no matter
-- which directory pandoc was invoked from.
local function inputDir()
  local files = PANDOC_STATE and PANDOC_STATE.input_files
  if files and files[1] then
    local dir = pandoc.path.directory(files[1])
    if dir ~= "" then return dir end
  end
  return "."
end

local function readFile(path)
  local handle = io.open(path, "r")
  if not handle then return nil end
  local text = handle:read("a")
  handle:close()
  return text
end

-- One block holding nothing but an image: swap it for its sidecar on the LaTeX
-- surface. Handles both Para (loose) and Plain (tight list / table cell). A figure
-- whose sidecar is missing is left as the image, so the PDF build never breaks over
-- one unrendered diagram.
local function swapForSidecar(el)
  if not FORMAT:match("latex") then return nil end
  if #el.content ~= 1 or el.content[1].t ~= "Image" then return nil end
  local src = el.content[1].src
  local sidecar = src:gsub("%.svg$", ".tex")
  if sidecar == src then return nil end
  local tex = readFile(pandoc.path.join({ inputDir(), sidecar }))
  if not tex then return nil end
  return pandoc.RawBlock("latex", tex)
end

function Para(el)
  return swapForSidecar(el)
end

function Plain(el)
  return swapForSidecar(el)
end
