/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.docs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.Algebra;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * WHO PROVIDED AN INSTRUCTION — the question the reference used to answer with an address alone.
 * <p>
 * The regression this pins: geo declares its coordinate casts at /m/inst/as (an address memory routes to /m),
 * so /m's page presented {@code as?point<=str} as its own while geo's page — the set that declared it — showed
 * nothing at all. The fix reads each set's DECLARATION list rather than only its addresses, which is what these
 * assertions measure: the author records the contract, the address owner does not claim it as a declaration,
 * and the row carries a link to the author's page.
 */
public class InstSetDocGeneratorTest extends AbstractMetatronTest {

    private static final String GEO = "/m/math/geo";
    private static final String M = "/m";

    @BeforeEach
    public void scope() {
        InstSetDocGenerator.documented(List.of(M, GEO));
        // the renderer's serializer is chosen once, AFTER the VM boots (an eager field initializer would touch the
        // type system while Obj.Helper.SERIALIZER is still null — the boot NPE this test's class documents)
        InstSetDocGenerator.initSerializers();
    }

    @AfterEach
    public void unscope() {
        InstSetDocGenerator.documented(List.of());
    }

    /** the contract geo's point kind declares at /m/inst/as — the row the issue is about. */
    private static Inst geoPointCast(final List<Inst> declared) {
        return declared.stream()
                .filter(inst -> inst.tid().toString().contains("/m/inst/as?")
                        && inst.tid().toString().contains(GEO + "/point"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("geo's point cast at /m/inst/as is not in its declaration list"));
    }

    @Test
    public void testAnAuthorRecordsAContractItsAddressCannot() {
        InstSet.importInstSet(f(GEO));
        final InstSet geo = (InstSet) Machine.read(f(GEO));
        final InstSet m = (InstSet) Machine.read(f(M));

        final Inst pointCast = geoPointCast(InstSetDocGenerator.declaredInsts(geo));

        // the ADDRESS is /m's, and the two questions have different answers
        assertTrue(InstSetDocGenerator.owns(pointCast.tid().toString(), M, List.of(M, GEO)),
                "the operation's address is /m's");
        assertFalse(InstSetDocGenerator.owns(pointCast.tid().toString(), GEO, List.of(M, GEO)),
                "and it is not geo's address");
        // the AUTHOR is geo, and the address owner must not claim the declaration
        assertFalse(InstSetDocGenerator.declaredInsts(m).stream()
                        .anyMatch(inst -> inst.tid().toString().equals(pointCast.tid().toString())),
                "/m holds the contract but did not declare it");
        // ... and geo does, for the row declared through the type's builder as well as for its own list
        assertTrue(InstSetDocGenerator.declaredInsts(geo).stream()
                        .anyMatch(inst -> inst.tid().toString().contains("/m/inst/has?")
                                && inst.tid().toString().contains(GEO + "/geometry")),
                "geo also records the has contracts it declares at /m/inst/has");
    }

    @Test
    public void testTheRowSaysWhoDefinedItAndPointsAtTheAuthorsPage() {
        InstSet.importInstSet(f(GEO));
        final Inst pointCast = geoPointCast(
                InstSetDocGenerator.declaredInsts((InstSet) Machine.read(f(GEO))));
        final String tid = pointCast.tid().toString();
        final Map<String, String> declaring = Map.of(tid, GEO);
        final Set<String> built = Set.of(M, GEO);

        final String ownerChip = InstSetDocGenerator.provenanceChip(pointCast, M, declaring, built, false);
        assertTrue(ownerChip.contains("defined by geo"), "the address page names the author: " + ownerChip);
        assertTrue(ownerChip.contains("m_math_geo.html#defined-__m__inst__as"),
                "and links to the author's page, at a STABLE operation anchor: " + ownerChip);

        final String authorChip = InstSetDocGenerator.provenanceChip(pointCast, GEO, declaring, built, true);
        assertTrue(authorChip.contains("address: <span class=\"text-danger\">/m</span>"),
                "the author's page names the address, in red: " + authorChip);
        assertTrue(authorChip.contains("m.html#__m__inst__as"),
                "and links to the page that documents it: " + authorChip);

        assertEquals("", InstSetDocGenerator.provenanceChip(pointCast, M, Map.of(tid, M), built, false),
                "a row this page both owns and declared carries no chip");
    }

    /**
     * EVERY DECLARED LAW HAS A TOOLTIP — the badges are generated from the registry, so a law the registry can
     * hold but {@link InstSetDocGenerator#lawDoc} cannot explain would render as a chip with nothing to hover.
     * The descriptions are checked over the whole enum, not a hand-written list, so a law added to
     * {@code Law} fails here rather than in a reader's face.
     * <p>
     * Not every law IS an equation — the effect and coefficient laws ({@code pure}, {@code blocked},
     * {@code floatable}, {@code partial}) state a property of the implementation or of the coefficient
     * algebra — so the LaTeX guarantee is that the equation-bearing majority carry it: prose for all, math for
     * the ones that can state one.
     */
    @Test
    public void testEveryDeclaredLawHasATooltipAndTheEquationsCarryLatex() {
        int equations = 0;
        for (final Law law : Law.values()) {
            final String doc = InstSetDocGenerator.lawDoc(law);
            assertFalse(doc.isEmpty(), "no tooltip for the declared law " + law);
            if (doc.contains("\\(") || doc.contains("\\[")) equations++;
        }
        assertTrue(equations >= Law.values().length - 2,
                "only the two laws that are not equations (`pure`, `blocked`) lack LaTeX — got "
                        + equations + " of " + Law.values().length);
    }

    /**
     * A mtron fragment is handed to Highlight.js — that means {@code pre > code.language-mtron}, escaped,
     * with the {@code mtron-inline} class the stylesheet needs to suppress highlightjs-copy's button (which
     * otherwise covers a one-line fragment and leaves the reader seeing only the surrounding words).
     */
    @Test
    public void testAnInlineMtronFragmentIsWhatTheHighlighterSelects() {
        final String html = InstSetDocGenerator.mtronInline("/m/math/geo/bbox::T");
        assertTrue(html.startsWith("<pre class=\"d-inline mb-0 align-baseline mtron-inline\"><code class=\"language-mtron\">"),
                "hljs.highlightAll() selects `pre code`, so the fragment must be one: " + html);
        assertTrue(html.contains("/m/math/geo/bbox::T") && html.endsWith("</code></pre>"), html);
        assertTrue(InstSetDocGenerator.mtronInline("a<=b").contains("a&lt;=b"),
                "the fragment is text: `<` must be escaped or the highlighter sees markup");
    }

    /** "November 12, 2026" — the building machine's date, no minute. */
    @Test
    public void testTheFooterStatesWhenThePageWasBuilt() {
        final String built = InstSetDocGenerator.lastBuilt();
        assertTrue(built.matches("[A-Z][a-z]+ \\d{1,2}, \\d{4}"),
                "expected a month-name, day, and year (no time): " + built);
    }

    /**
     * THE ALGEBRA PANEL IS A NATIVE {@code <dialog>}, NOT A BOOTSTRAP MODAL. The page lives inside
     * {@code <main>}, which the site's stylesheet makes a stacking context ({@code position:relative;
     * z-index:1}), so a fixed-position Bootstrap modal rendered inside it sits UNDER the body-level
     * backdrop: the page dims, the panel dims with it, and neither the X nor the backdrop can close it
     * (the reader had to reload). {@code showModal()} puts the dialog in the browser's top layer, which
     * escapes the ancestor stacking context; the label is plain {@code algebra}, with no ellipsis.
     */
    @Test
    public void testTheAlgebraPanelEscapesTheSitesStackingContext() {
        final String html = InstSetDocGenerator.algebraDialogHtml("algebra-x", "int::T algebra", "law=>[…]");
        assertTrue(html.contains("<dialog class=\"algebra-dialog\""),
                "a native dialog, opened in the top layer: " + html);
        assertTrue(html.contains("showModal()"), "showModal() escapes the ancestor stacking context: " + html);
        assertTrue(html.contains("this.close()"), "closable by its own button and by the backdrop: " + html);
        assertFalse(html.contains("data-bs-"), "not a Bootstrap modal — that one renders under the backdrop");
        assertTrue(html.contains(">algebra</button>"), "the label carries no ellipsis: " + html);
    }

    /**
     * THE WEB VIEW OF AN ALGEBRA — one block per structural theory, its roles as rows. The view is read
     * through {@link Algebra} ({@code theories()}, {@code theory(name)}) rather than by rec-at ceremony, and
     * this pins the shape a reader gets: the theory's chip identity, its declared type, and a row per role
     * with the value bound to it.
     */
    @Test
    public void testTheAlgebraWebViewNamesEachTheoryAndBindsItsRoles() {
        final Rec ring = rec(uri("zero"), jnt(0), uri("one"), jnt(1));
        final Algebra algebra = Algebra.of(rec(uri("ring"), ring));
        final String html = InstSetDocGenerator.algebraTheories(algebra, Set.of(M));
        assertTrue(html.contains("class=\"algebra-theory\""), "one block per theory: " + html);
        assertTrue(html.contains("theory-chip theory-ring"), "the theory keeps its chip identity: " + html);
        assertTrue(html.contains("algebra-role\">zero<") && html.contains("algebra-role\">one<"),
                "each role is a row: " + html);
        assertTrue(html.contains(">0<") && html.contains(">1<"),
                "an element role prints its literal: " + html);
    }

    /**
     * AN OPERATION ROLE LINKS TO ITS OWN CARD — {@code add => plus} is the next thing a reader wants to see,
     * so the web view hands them the instruction's card rather than a dead name; an element role stays the
     * literal it is.
     */
    @Test
    public void testAnOperationRoleLinksToItsInstructionCard() {
        final Inst plus = ((InstSet) Machine.read(f(M))).insts().stream()
                .filter(inst -> "plus".equals(inst.tid().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("plus is not among /m's instructions"));
        final String html = InstSetDocGenerator.roleValueHtml(plus, Set.of(M));
        assertTrue(html.contains("m.html#__m__inst__plus"),
                "an operation role links to its card: " + html);
        final String literal = InstSetDocGenerator.roleValueHtml(jnt(0), Set.of(M));
        assertTrue(literal.contains(">0<") && !literal.contains("<a "),
                "an element role is the literal, not a link: " + literal);
    }

    /** The language's own spelling is its own TAB — one click away, never the only view. */
    @Test
    public void testTheMtronEncodingIsProvidedBesideTheWebView() {
        final Rec law = rec(uri("ring"), rec(uri("zero"), jnt(0)));
        final String body = InstSetDocGenerator.algebraTheories(Algebra.of(law), Set.of(M));
        assertFalse(body.contains("language-mtron"), "the structured view is not the encoding: " + body);
        final String panel = InstSetDocGenerator.algebraDialogHtml("algebra-x", "int::T algebra",
                InstSetDocGenerator.algebraTabs("algebra-x", body,
                        InstSetDocGenerator.algebraEncoding("law=>[ring=>…]")));
        assertTrue(panel.contains("data-bs-target=\"#algebra-x-mtron\""),
                "the encoding is a tab of the panel: " + panel);
        assertTrue(panel.contains(">theories</button>") && panel.contains(">mtron encoding</button>"),
                "both views are named: " + panel);
        assertTrue(panel.contains("class=\"algebra-mtron\""),
                "the encoding scrolls in its own box (with the site's slim scrollbar): " + panel);
        assertFalse(panel.contains("<details"), "no disclosure widget — the tab replaces it");
    }

    /**
     * THE THEORY CHIP IS A LINK — {@code (ring)} in the panel goes to the page that defines
     * {@code /m/theory/ring_theory}, which is the instruction-set reference itself.
     */
    @Test
    public void testATheoryChipLinksToTheTheoryTypesPage() {
        final Rec instance = rec(uri("zero"), jnt(0));
        final Algebra algebra = Algebra.of(rec(uri("theory_of_rings"), instance));
        final String html = InstSetDocGenerator.algebraTheories(algebra, Set.of(M));
        // the synthetic instance carries REC_TID, whose page is /m: a chip link is still produced
        assertTrue(html.contains("<a class=\"text-decoration-none\" href=\""),
                "a theory chip is wrapped in a link when its type is documented: " + html);
        assertTrue(html.contains("theory-chip theory-theory_of_rings\">theory_of_rings</span></a>"),
                "the link wraps the chip itself, not just its label: " + html);
    }

    /**
     * THE SET LINKS ITS OWN SKILLS DOCUMENT — the hand-written instruction-set reference exists in two
     * renditions beside the markdown, and the page that documents the set should say so: the same
     * {@code .article-format} chips the header's dropdowns use, in the masthead's meta column.
     * The render is driven by the source markdown's presence, so a set without a skills document gets no chip
     * rather than a link to a page the site never publishes.
     */
    @Test
    public void testTheReferencePageLinksBothSkillDocRenditions() {
        final String html = InstSetDocGenerator.skillDocLinks(M, "..");
        assertTrue(html.contains("href=\"../skills/mtron/references/m-instset-mtron.html\">html</a>"),
                "the rendered reference is one click away: " + html);
        assertTrue(html.contains("href=\"../skills/mtron/references/m-instset-mtron.md\">md</a>"),
                "and so is the markdown the site serves: " + html);
        assertTrue(html.contains("class=\"article-format\""),
                "the chips reuse the nav dropdown's format style: " + html);
        assertTrue(html.contains(">skill:</span>"),
                "the chips say WHOSE html/md they are: " + html);
        assertTrue(html.indexOf(">skill:</span>") < html.indexOf(">html</a>"),
                "the label precedes the chips: " + html);
        assertEquals("", InstSetDocGenerator.skillDocLinks("/m/db/dcmnt", ".."),
                "dcmnt has no skills reference — no chip may point at a page that was never written");
    }

    /**
     * THE MASTHEAD AND THE SECTION BAR ONLY STATE WHAT THIS PAGE HOLDS — a zero is noise, not a fact, and a
     * section link that points at an id the page never renders is a dead click. Both are driven by the same
     * counts, so they can never disagree about which sections exist.
     */
    @Test
    public void testTheMastheadAndSectionBarOmitWhatThePageDoesNotHave() {
        final String stats = InstSetDocGenerator.statTiles(0, 49, 2, 354, 7, 0);
        assertTrue(stats.contains(">49<") && stats.contains(">types<"), "the set's size is stated: " + stats);
        assertTrue(stats.contains(">354<") && stats.contains(">insts<"), stats);
        assertFalse(stats.contains(">0<"), "a zero-count tile is noise: " + stats);
        assertFalse(stats.contains("defined elsewhere"), stats);

        final String links = InstSetDocGenerator.subnavLinks(0, 49, 2, 354, 0, 7);
        assertTrue(links.contains("href=\"#types\""), links);
        assertTrue(links.contains("href=\"#instructions\""), links);
        assertFalse(links.contains("href=\"#consts\""), "an empty section gets no link: " + links);
        assertFalse(links.contains("href=\"#defined\""), "no external definitions, no link: " + links);

        final String both = InstSetDocGenerator.subnavLinks(1, 0, 0, 0, 2, 0);
        assertTrue(both.contains("href=\"#consts\"") && both.contains("href=\"#defined\""), both);

        final String script = InstSetDocGenerator.sectionScript();
        assertTrue(script.contains("instset-subnav") && script.contains("scrollY > 300"),
                "the bar tracks the same 300px navbar threshold: " + script);
    }

    /**
     * THE INSTRUCTION FILTER — a labelled search box and a polite live count above the cards. The count
     * is the number of CONTRACTS, not names: one card can hold several polymorphic variants, so the
     * helper states the section's own total (354 on {@code /m}) rather than a card count.
     */
    @Test
    public void testTheInstructionFilterIsALabelledLiveSearchOverEveryContract() {
        final String html = InstSetDocGenerator.instFilterHtml(354);
        assertTrue(html.contains("type=\"search\""), "the filter is a search input: " + html);
        assertTrue(html.contains("placeholder=\"filter insts…\""), "the placeholder names the filter: " + html);
        assertTrue(html.contains("<label") && html.contains("for=\"instset-inst-filter\""),
                "the input is labelled, not merely placeholdered: " + html);
        assertTrue(html.contains("aria-live=\"polite\""), "the count is a polite live region: " + html);
        assertTrue(html.contains("showing 354 of 354"), "the count starts at every contract: " + html);

        final String small = InstSetDocGenerator.instFilterHtml(7);
        assertTrue(small.contains("showing 7 of 7"), "the helper states the page's own total: " + small);
        assertFalse(small.contains("354"), "no page's count is baked in: " + small);
    }

    /**
     * THE FILTER'S BEHAVIOUR — the same page script the scroll-spy lives in matches each card's visible
     * text, hides whole cards (so no gap is left) and resets on Escape; a page without cards just skips it.
     */
    @Test
    public void testThePageScriptFiltersCardsAndClearsOnEscape() {
        final String script = InstSetDocGenerator.sectionScript();
        assertTrue(script.contains("instset-inst-filter"), "the script finds the filter: " + script);
        assertTrue(script.contains("instset-inst-card"), "and the cards it filters: " + script);
        assertTrue(script.contains("textContent"), "a card is matched by its visible text: " + script);
        assertTrue(script.contains("Escape"), "Escape resets the filter: " + script);
        assertTrue(script.contains(".hidden = !hit"), "a miss is hidden, not removed from the DOM: " + script);
    }

    /**
     * THE BREADCRUMB WALKS THE SET'S OWN PATH — {@code instruction sets: / m / math / geo} — and only the
     * ancestors that were part of this build are links. A folder with no page of its own ({@code /m/db})
     * and the leaf (the page the reader is on) are plain text, so a crumb never points at a 404.
     */
    @Test
    public void testTheBreadcrumbWalksTheHierarchyAndLinksOnlyRealPages() {
        final String crumb = InstSetDocGenerator.breadcrumb("/m/math/geo",
                Set.of("/m", "/m/math", "/m/math/geo"), "..");
        assertTrue(crumb.contains("href=\"../instset/index.html\">instruction sets</a>"), crumb);
        assertTrue(crumb.contains("href=\"../instset/m.html\">m</a>"), crumb);
        assertTrue(crumb.contains("href=\"../instset/m_math.html\">math</a>"), crumb);
        assertTrue(crumb.contains(">geo</span>"), "the leaf is the current page, not a link: " + crumb);

        final String folderOnly = InstSetDocGenerator.breadcrumb("/m/db/tble", Set.of("/m", "/m/db/tble"), "..");
        assertFalse(folderOnly.contains("href=\"../instset/m_db.html\""),
                "no page exists for /m/db, so it is not a link: " + folderOnly);
        assertTrue(folderOnly.contains(">db</span>"), folderOnly);
    }
}
