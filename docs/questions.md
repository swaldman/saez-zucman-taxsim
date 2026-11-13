# Open questions for Emmanuel Saez and Gabriel Zucman

This document collects features of the January 2020 simulator (`reference/taxsimulator2.xlsx`)
that we reproduce faithfully but don't fully understand, or aren't sure we should carry forward.
For each one we'd like to know three things:

- why it's there;
- whether it's a deliberate modeling choice we should keep;
- whether, as we update the data and extend the model to other countries, we should correct it or
  generalize it.

Each entry gives the context (with spreadsheet cell references), what our port does today, and the
questions. Cell references are to sheet `calculatorbig` unless noted. The model is described in full
in [`methodology.md`](methodology.md), and the generic engine in [`architecture.md`](architecture.md).

Priorities: **[High]** affects results materially or blocks generalizing to other countries;
**[Medium]** affects results at the top or in specific plans; **[Low]** for completeness.

---

## 1. Broad taxes reducing the income and corporate taxes [High]

**Context.** When a national income tax (rate share R, column R) or a VAT (share S, column S) is
introduced, the proposed corporate and individual income taxes are multiplied by (1 − R − S):

- corporate tax: `V = (1 − R − S) · corp · K_p/K_c`
- individual income tax: `W = (1 − R − S) · max(ditax + …, 0)`

No other tax gets this adjustment: payroll, sales/excise, property, estate, wealth and health are
all left alone.

**What we do.** We reproduce this exactly for the US. In the generic engine we generalized the
factor to `(1 − Σ proposed broad-tax rates) / (1 − Σ current broad-tax rates)`. For the US, where
current broad taxes are zero, that is the same thing. For countries that already have a VAT, it
avoids reducing the observed income tax when nothing changes. Which taxes are reduced this way is
set separately for each country.

**Our reading: probably not deductibility.** Mechanically, the adjustment is the same as deducting
the broad tax from the income-tax base, with the deduction valued at each group's *average* income
tax rate. We doubt literal deductibility is meant, for three reasons:

- VATs aren't deductible from personal income taxes in the US or (as far as we know) anywhere else.
  The US deduction for state and local taxes, which is partial and capped, is a different matter,
  and the model doesn't represent it.
- A real deduction would reduce tax at the *marginal* rate, not the average rate. Under a
  progressive income tax that difference is large at the top.
- The same factor applies to the corporate tax, where "deductibility" has no obvious meaning.

**Our hypothesis: an incidence effect.** We suspect the adjustment stands for an indirect effect: a
broad tax borne by factors of production reduces pre-tax factor incomes, and so shrinks the bases of
income-based taxes. For the VAT, if it isn't passed into higher consumer prices (for example because
monetary policy doesn't accommodate it), nominal wages and profits fall by roughly the tax, and the
income and corporate taxes collect proportionally less. US scorekeepers (CBO, JCT) apply a similar
"income and payroll tax offset" to indirect taxes.

**Questions.**

1. **Is that hypothesis right?** If not, what is the intended rationale for the (1 − R − S) factor?
2. **Why not payroll taxes?** If broad taxes work by lowering factor incomes, wages fall too, so
   payroll taxes and social contributions should shrink as well. The CBO/JCT offset covers payroll
   taxes for this reason. Was leaving payroll out deliberate? For example, because payroll taxes are
   capped, or because of a view about who bears them?
3. **Should some parts of the income tax be exempt?** The factor scales the whole effective income
   tax rate, including refundable credits (EITC, refundable CTC). Those depend on earnings and family
   circumstances in ways that may not shrink with the base, and phase-ins and phase-outs could even
   move the other way. Should credits, or other parts that don't move with the base, be excluded?
4. **Average rate or marginal rate?** Is a proportional reduction at the average rate intended?
   Would a reduction at each group's marginal rate be more appropriate? The data already include
   average marginal rates by group (`mtr`).
5. **Other countries.** For countries that already have large VATs and social contributions, would
   you treat social contributions as reduced by a VAT increase? Is our relative form of the factor,
   (1 − Σ proposed)/(1 − Σ current), consistent with how you think about it?

**Why it matters.** This is the central interaction between taxes in the model. For a European-style
country, the answers determine how much a VAT increase actually raises once the income tax and
social contributions are accounted for.

---

## 2. The VAT formula and its incidence [High]

**Context (column S).** The VAT is computed as

`v/(1+v) · 0.4 · (0.75 · salestax(g)/salestax̄ + 0.25)`

That is: a tax-inclusive rate, on a base of 40% of national income. Three quarters of the base is
distributed in proportion to the current sales-tax profile (the ITEP-based allocation) and one
quarter in proportion to income.

**Questions.**

1. **The 40% base.** Where does it come from? Is it consumption net of exemptions, a typical VAT
   "C-efficiency", or something else? Should it change with the base the user chooses (reduced rates,
   exempt services)?
2. **The split.** Appendix §1.1 assigns *existing* consumption taxes 70% to consumers and 30% to
   factors; the VAT uses 75%/25%. Is the difference deliberate?
3. **The profile.** US sales taxes largely exempt services, and the appendix notes this matters at
   the top. A broad VAT would tax services that the sales-tax profile doesn't capture. Should a VAT
   follow a broader consumption profile, for example from the Consumer Expenditure Survey or from
   disposable income and saving rates? The workbook computes saving-rate and consumption profiles in
   columns CK–CS, but the VAT doesn't use them. What were those columns for?
4. **Countries with existing VATs.** We rescale the observed VAT incidence by the change in
   v/(1+v). Is that how you would model a rate change there?

---

## 3. Wealth above $1 billion [Medium]

**Context.** The data report average wealth in excess of thresholds only up to $1bn
(`databig` columns `wealth_0m`…`wealth_1000m`). For the top-400 line alone, the excess above
$2.5bn, $5bn and $10bn is imputed as 0.9, 0.6 and 0.45 × the excess above $1bn
(calculator!AQ22:AS22, which calculatorbig!AQ135:AS135 refers to). Every other group, including
P99.999–top 400 (which has $670m on average above $1bn), has zero excess above $2.5bn. So wealth-tax
brackets above $2.5bn reach only the top 400.

**Questions.**

1. **The factors.** How were 0.9 / 0.6 / 0.45 obtained? From the Forbes 400 distribution?
2. **The group just below the top 400.** Is it intended that P99.999–top 400 pays nothing in
   brackets above $2.5bn? Or should those groups also get imputed values?
3. **Going forward.** Our generic engine can extrapolate above the highest observed threshold
   assuming a Pareto tail. Would you prefer that, a Forbes-based imputation, or something else?

---

## 4. Foreign profits counted twice in the proposed federal corporate rate [Medium]

**Context.** The current federal corporate rate is `Q142 = 150/1308 + Q143`, with `Q143 = 8/1308`
(the current tax on foreign profits). The proposed rate is `R142 = R141 − (Q141 − Q142) + R143`.
Because Q142 already includes Q143, R142 contains *both* the current foreign-profits term (8/1308)
and the reformed one (R143, either 8/1308 or 96/1308). R142 is used only when dividends and the
corporate tax are integrated (columns BW, CA).

**Question.** Is the double count intended, or should it be `R142 = R141 − Q141 + 150/1308 + R143`?
If it is a slip, its effect is small and limited to plans with integration, such as the "book" plan.

---

## 5. Levers in the interface that the calculator doesn't use [Medium]

**Context.** Interface rows 12–14 offer a "Narrow employer payroll tax", a "Broad employer payroll
tax" and an "Adjusted Gross Income base", all marked "not programmed yet". No formula uses them.

**Questions.** How were these meant to work: the bases, the incidence, and how they interact with
existing taxes? We'd like to implement them, since they are natural levers for many countries.

---

## 6. The individual income tax calculation [Medium]

**Context (columns BB–CI, W).**

1. **Pass-through of bracket changes.** When dividends and capital gains keep their preferential
   treatment, the extra tax from changing the brackets is scaled by the share of ordinary income in
   AGI, `(1 − (kg + div)/agi)` (column CB). But the bracket bases are *taxable* income in excess of
   thresholds, not AGI. Is the AGI share used as an approximation of the ordinary share of taxable
   income at the top?
2. **Retained earnings of closely held C-corporations.** The $166.6bn (BB138, from Table D2) is
   allocated in proportion to realized capital gains plus dividends. Was that proportionality chosen
   for lack of better data on who owns closely held corporations?
3. **The 20% preferential rate.** The integration formula (BW) uses `min(0.2, mtr)` as the current
   preferential rate on dividends, which ignores the 3.8% net investment income tax. Is that
   intentional?
4. **The floor.** `max(…, 0)` keeps a group's income tax rate from going negative when brackets are
   cut. That means refundable credits can't grow under a reform. Is that intended?
5. **The switches.** The dividend, capital-gains and integration switches mean "different from
   current law" (AA141–AA143), not "on". With today's current law they amount to the same thing.
   Should a plan that *restores* preferences relative to a future current law (one that taxes
   capital gains fully) be modeled symmetrically?

---

## 7. Estate tax, health head tax and other fixed constants [Medium]

**Context.**

- **Estate tax.** `U = (MTR/0.4) · estate(g) · (1 − avoidance)/(1 − 0.6)`. This scales the observed
  estate tax linearly in the top rate, as if all estate tax were collected at the top rate, and the
  exemption threshold is not a lever.
- **Health head tax.** `Y = payroll + 0.55 · healthtax(g)` when the employer head tax is on.

**Questions.**

1. Is the linear scaling of the estate tax a deliberate simplification? Would you model the
   exemption threshold (for example with an excess curve over estates)?
2. Where does 0.55 come from? Is it the employer share of premiums, applied to the profile of private premiums (`healthtax`)?
3. For each hard-coded constant, what is the source, and how should it be updated?
   - 1308: C-corporation profits ($bn)
   - 150: federal corporate tax
   - 8 and 96: tax on foreign profits, current and reformed
   - 166.6: retained earnings of closely held C-corporations
   - 0.4, 0.75 and 0.25: the VAT
   - 0.55: the health head tax
   - 0.6 and 0.4: estate avoidance and top rate
   - 17,546 × 1.038: national income

   Are any of them derived in `SZ2019AppendixTables.xlsx` or the Stata programs in a way we should
   reproduce?

---

## 8. Converting to dollars: national income versus the groups' total income [Low]

**Context.** Revenue as a share of income is computed over the 128 groups. Their total income
Σ n·y (B137 × C137) is about $18,003bn: adults above half the minimum wage, plus pure capital gains.
That share is then multiplied by national income, 17,546 × 1.038 ≈ $18,213bn (interface Q8).

**Question.** Is applying the restricted population's ratio to full national income intended? The
excluded bottom ~10% of adults pay taxes too (notably consumption taxes), and pure capital gains are
not part of national income.

---

## 9. The long-run behavioral block [Medium]

**Context (CU–DO, EO–FD).**

- Implied marginal tax rates come from the slope of after-tax on pre-tax income between neighboring
  groups. At both ends the workbook compares with an empty row (0, 0), so at the edges the slope
  becomes the average net-of-tax rate.
- The rates are then smoothed by seven passes of a 7-point moving average.
- The bargaining response phases in linearly from P89 (weight 0 at the P89–90 group, 0.1 at P90–91)
  to full strength at P99 and above. The column header says "top 1% only and phase-in top 10-1%".
- The income lost at the top through bargaining is redistributed in proportion to each group's
  "missing income" relative to equitable growth since 1980, floored at zero.

**Questions.**

1. Are the edge treatment and the amount of smoothing (7 × 7) deliberate? The edge treatment affects
   the top 400's implied rate most.
2. Does a phase-in starting at P89 rather than P90 match your intent?
3. When we update the data, should "equitable growth since 1980" keep 1980 as the base year? Which
   growth benchmark should we use?
4. The website presents these responses as experimental. Do you consider them ready to show in an
   updated tool?

---

## 10. Data construction questions for the update [High]

These aren't quirks, but we need answers to refresh the US data and build other countries.

1. **Latest data.** What is the latest year of the US DINA micro-files (`usdina`)? Could you share
   the program that builds the simulator inputs (`taxsimuweb.do`, producing `taxsimgdecilepeinc`),
   with the variables `wealth_Xm`, `taxinc_Xk`, `nitaxbase`, `mtr`, `mtrextra`, `dicsh`?
2. **The top 400.** Do you still use the adjustment that sets their fiscal income to 45% of their
   economic income? Is there a newer estimate?
3. **Health insurance.** For `healthtax`, should we keep the W-2-based allocation of employer
   premiums (2015 internal data, scaled to the national total)?
4. **Current law.** For current US law after the 2025 changes, would you recompute individual
   income taxes with TAXSIM? Do you have a preferred approach?
5. **Other countries.** Which datasets would you start from for other countries: EU-DINA, WID, or
   the Fisher-Post–Gethin redistribution database? Have you, or the EU Tax Observatory, built similar
   per-group tax-incidence files (including excess curves) for European countries that we could use?
6. **A denser grid of excess thresholds.** Brackets between the data's thresholds are interpolated
   within each group (see [`architecture.md`](architecture.md), "Known limits of the excess
   curves"). Wherever a threshold falls between grid points, the data allow a wide range of
   values: for the 2018 data, summed over all groups, the range is 12–31% of the estimate (for
   example $709bn–$813bn of taxable income above $3m, and $3.1–3.7tn of wealth above $750m). Could
   the data-building program output `wealth_Xm` and `taxinc_Xk` on a denser grid? For example,
   about four thresholds per factor of ten, from $10k to $100bn, using the same grid for every
   country. Alternatively, could it provide the share of each group's adults above each threshold
   alongside the average excess? Either would pin down off-grid brackets far better than any
   interpolation.

---

## 11. Stale pasted curves [Low; no action needed]

**Context.** The workbook's pasted plan curves EE ("book") and EF ("current"), with health shown as
a tax, differ by up to 2e-3 in the top 0.1% from what the current data and formulas produce. EE
should equal EL, since the book plan fully replaces private insurance, but it doesn't. The other 12
pasted curves match exactly.

**Question.** Just to confirm: were these pasted from an earlier data version, so the current
formulas and data are authoritative?

---

## 12. Stored total tax rate versus the sum of its components [Low; no action needed]

**Context.** `effrate` in the data differs from the sum of its components (sales + property +
payroll + income + corporate + estate) by up to 1e-8. This looks like float32 rounding in the Stata
output. Our generic engine uses the sum, so a zero reform raises exactly zero revenue.

**Question.** Just to confirm: is this only storage precision?

---

## 13. Why some taxes are adjusted additively and others proportionally [High]

**Context.** The model never computes current taxes from the statutes. It anchors on each group's
*observed* effective rate for each tax and applies only the *change* implied by a reform. The gap
between what the textbook rules would collect and what is observed (deductions, credits, state
taxes, avoidance, evasion, timing) is never estimated. It is held fixed under reform, in one of two
ways:

- **Additive (individual income tax).**
  `new rate = observed rate + [T(proposed brackets) − T(current brackets)] / income`,
  with T computed on taxable-income excess curves (column W). The residual is held fixed *in
  amount*: credits, state income taxes and avoidance don't change when federal brackets do.
- **Proportional (corporate, estate, sales, private health insurance).**
  `new rate = observed rate × (proposed aggregate / current aggregate)`. The corporate tax, for
  example, is scaled by `(rate_p + foreign_p) / (rate_c + foreign_c)` on $1,308bn of profits
  (column V). The residual (state corporate taxes, profit shifting, credits) is held fixed *in
  proportion*.

The corporate lever is an *effective* rate (current: 210/1308 ≈ 16%), not the 21% statutory rate.
Your January 2020 summary translates candidates' statutory proposals into effective rates by
judgment, for example "federal nominal rate back to 35% ⇒ effective fed+state rate up to 25%".

**Questions.**

1. **The two regimes.** Is the choice between additive and proportional adjustment deliberate for
   each tax? What's the reasoning? For instance, why should the income-tax residual stay fixed in
   amount while the corporate residual scales?
2. **Stability under large reforms.** How stable do you expect these residuals to be under large
   reforms, such as top marginal rates of 55–68% or a 30% effective corporate rate? Would you model
   any avoidance response beyond the wealth-tax evasion and estate-tax avoidance parameters?
3. **Statutory to effective.** Is there a documented rule for translating statutory corporate rates
   (and minimum taxes or foreign-profit rules) into the effective-rate lever? If so, we'd like to
   implement it, so users can enter statutory rates.

---

## 14. Fiscal income relative to economic income under reform [High]

**Context.** The income tax applies to *fiscal* income: realized gains, dividends, AGI, taxable
income. Rates are measured against *economic* income. Each group's ratio of the two comes from
DINA, where both are measured on the same synthetic individuals. For the top 400 the ratio is
capped at 45%. Under reform the model holds this relationship fixed:

- Taxing capital gains as ordinary income (with the step-up at death eliminated) is assumed not to
  change realizations. The workbook's note (calculator AB30) says "assume that step up + full tax
  does not reduce K gains".
- Taxing dividends fully is not modeled as changing payout. The workbook notes (AB28) that "full
  tax would shrink divs. and increase KG", but this isn't implemented.
- Integration is modeled with no behavioral response (AB29).

Many of the plans work precisely by narrowing the gap between fiscal and economic income: full
taxation of gains, taxing gains at death, mark-to-market, integration, a national income tax. So
the ratio's stability matters a lot for them.

**Questions.**

1. **Static convention.** Is holding realizations and payouts fixed intended as a static-scoring
   convention, to be read alongside the long-run behavioral block?
2. **Realization responses.** For plans that tax gains fully but only on realization, would you
   apply a realization elasticity, or rely on taxation at death to limit deferral?
3. **Mark-to-market.** How should accrual taxation (Warren, Buttigieg) be represented? By raising
   the fiscal-to-economic ratio toward the share of income that consists of accrued gains?
4. **The top 400.** Does the 45% figure for the top 400 interact with these reforms? For example,
   does taxing gains at death or on accrual effectively lift it?
5. **Pure capital gains by group.** In `databig`, `denom − avg` equals 0.366 × realized gains
   (`fikgi`) in every group (to rounding). So the aggregate pure share of realized gains (gains above 3% of
   national income) seems to be applied uniformly. Is that intended? Retained earnings, and so the
   non-pure part of gains, are concentrated among equity owners. A group-specific split could put a
   different share of each group's gains in the denominator, which matters most at the top, where
   pure gains are 10–18% of `avg`. Relatedly, which national income total defines the 3%? Our
   estimate of the implied pure share depends on it.

---

## 15. Definition of the national income tax base (`nitaxbase`) [Medium]

**Context.** The flat national income tax is `rate × nitaxbase / denom` (column R). `nitaxbase` is
an economic concept, but it is narrower than the rate denominator:

| Population | `nitaxbase` as a share of `denom` |
|---|---|
| All groups | about 91% |
| Range across groups | 63% (near the bottom) to nearly 100% |
| Top 400 | about 89% of pre-tax income (`avg`), excluding pure capital gains |

The "book" plan uses a 7% national income tax, and the Sanders plan approximates his employer
payroll tax and CEO-pay surtax as a 2% national income tax.

**Questions.**

1. **The definition.** What exactly does `nitaxbase` include and exclude? For example: imputed
   rent, investment income of pension funds, employer pension and health contributions, retained
   earnings, income of non-filers, pure capital gains.
2. **The book's tax.** Is it the base of the national income tax proposed in *The Triumph of
   Injustice*, which taxes all income including retained earnings?
3. **Interactions.** Is it net of any existing taxes? How should it change if private health
   premiums become cash wages, as in the Sanders and Warren plans?

---

## 16. The behavioral block at very high combined rates [Medium]

**Context.** The long-run responses (section 9) multiply pre-tax income by
`((1 − m_p)/(1 − m_c))^ε`, using constant elasticities: 0.25 for output, plus 0.5 for bargaining
at the top. The proposed implied marginal rate `m_p` is capped at 0.99. Under the most ambitious
plans, the top groups face proposed marginal and average rates that are far outside anything
observed. For the top 400, with health insurance counted as a tax, after-tax income per adult is:

| | Pre-tax income | Total tax rate | After-tax income |
|---|---|---|---|
| 2018, actual | $457m | 23% | $351m |
| Sanders, static | $457m | 98% | $8.0m |
| Sanders, long-run | $107m | 98% | $1.9m |
| Warren, long-run | $189m | 86% | $25.6m |

Two things drive these numbers:

- **Long-run pre-tax income falls about 77% under Sanders.** That is largely a consequence of the
  functional form as `m_p` approaches the cap, not of any observed response.
- **The 98% average rate is mostly the wealth tax.** It is applied to an unchanged wealth base,
  although a tax of that size would erode the base over time.

Compared with the "equitable growth" benchmark (each group's 1980 after-tax income scaled by
average after-tax growth: $36.5m for the top 400), Sanders' plan would leave the top 400 at about
1/19 of the path. That is also about 1/12 of their 1980 income in real terms. Our website now
plots "raise still needed to reach the path", and at the top under Sanders it reaches about
+1,800%.

**Questions.**

1. **Range of validity.** Is the behavioral block meant to be used at combined marginal and
   average rates this high? Is there a range of rates beyond which you'd advise against showing
   it?
2. **Functional form.** Would you prefer a different form at high rates, for example a lower cap
   on `m_p` or elasticities that decline as rates rise?
3. **Dynamic wealth.** For a wealth tax at these rates, should the static calculation be paired
   with a projection of the declining wealth base (as in your wealth-tax revenue estimates), rather
   than taxing 2018 wealth indefinitely?
4. **Presentation.** Is "raise still needed to reach the equitable path" a measure you're
   comfortable with? Or would you rather we show the plans' percentage changes in income directly?

---

## 17. Rounded wealth values in the top two rows [Low]

**Context.** In `databig`, most values are stored at full precision. But in two rows the wealth
columns are rounded to three significant figures:

| Row | Rounded columns | Not rounded |
|---|---|---|
| P99.999–(top 400) | `hweal` and `wealth_0m` … `wealth_250m` (e.g. 1.23e9, 1.22e9, 1.21e9) | `wealth_500m`, `wealth_1000m` |
| Top 400 | `hweal` and every `wealth_Xm` (4.27e9 for $0–$10m, then 4.26e9, 4.25e9, …, 3.77e9) | – |

The income and tax columns in these rows are not rounded. The trailing digits (e.g. 4,270,000,128)
are what single-precision storage of a rounded number produces, so the rounding is in the source
file rather than in our extraction.

The effect on wealth-tax revenue is small: rounding moves these groups' excesses by less than 0.5%.
But it distorts the shape of their excess curves. For the top 400, six identical values from $0 to
$10m imply that the excess doesn't fall at all over that range, when it should fall dollar for
dollar, since all 400 are above $10m. Steps like this are where our scan found the largest
convexity violations ([`architecture.md`](architecture.md), "Known limits of the excess curves").

**Questions.**

1. Is the rounding deliberate, for example for disclosure control of the very top? Or is it an
   artifact of the export?
2. If it's for disclosure, would you be comfortable sharing these rows at full precision for this
   use, or should we keep the rounded values and smooth the curves ourselves?
