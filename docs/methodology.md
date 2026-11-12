# The Saez–Zucman tax simulator: methodology as implemented

This document specifies the model behind [taxjusticenow.org](https://taxjusticenow.org) as it is
implemented in `reference/taxsimulator2.xlsx` (revised January 20, 2020), and as ported to
`core/src/taxsim/sz/`. Every formula is tied to the spreadsheet cells it comes from (sheet
`calculatorbig` unless noted; row 8 is the first group, rows 141–156 hold parameters).

Background sources: `reference/SZ2019Appendix.pdf` (data construction) and
`reference/taxsimsummary_v2.pdf` (the 2020 candidate plans).

## 1. Unit of analysis and data

- **Source.** The per-group inputs come from the US Distributional National Accounts (DINA):
  [Thomas Piketty, Emmanuel Saez and Gabriel Zucman (2018), "Distributional National Accounts:
  Methods and Estimates for the United States," *Quarterly Journal of Economics* 133(2):
  553–609](https://academic.oup.com/qje/article-abstract/133/2/553/4430651)
  ([NBER working paper w22945](https://www.nber.org/system/files/working_papers/w22945/w22945.pdf);
  data and programs at [gabriel-zucman.eu/usdina](https://gabriel-zucman.eu/usdina)). DINA
  distributes all of national income, and all taxes, across synthetic adult individuals built
  from tax-return microdata and surveys, so that group totals add up to the national accounts. The
  simulator uses DINA values for 2018 (projected to that year as described in
  `SZ2019Appendix.pdf`), aggregated into the groups below.
- **Population.** Adults (20+), incomes of married couples split equally, ranked by *pre-tax
  national income plus "pure" capital gains*. "Pure" capital gains are realized taxable gains in excess
  of 3% of national income, treated as asset-price gains rather than retained earnings. Adults with
  pre-tax income below half the federal minimum wage (≈ $7,500) are excluded. That excludes about
  10% of adults and almost no income.
- **Groups.** 128 groups: percentiles P0–1 … P98–99, then P99–99.1 … P99.9–99.91 … P99.99–99.991 …
  P99.999–(top 400), and the top 400 adults (`databig` rows 2–129; `gperc` 400 = top 400).
- **Per-group inputs** (`databig`; see `SzGroup` for the full mapping). Number of adults,
  average income (`denom`), and current effective rates by tax: sales/excise, property,
  payroll/social contributions, individual income (federal + state), corporate, estate, and
  employer-sponsored private health insurance. There is also fiscal income composition (realized
  capital gains, dividends, AGI) and average wealth. Wealth and taxable income enter as **average
  excesses above thresholds** (`wealth_Xm`, `taxinc_Xk`); these are what any bracket schedule needs
  (§5). The remaining inputs are the national-income-tax base, the average ordinary marginal rate
  (`mtr`), the average marginal rate above 20% (`mtrextra`), and disposable cash income.
- **Incidence conventions** (Appendix §1.1):
  - 70% of consumption taxes fall on consumers (allocated following ITEP state profiles) and
    30% on factors of production, in proportion to factor income.
  - Corporate tax falls on owners of C-corporation equity.
  - Property tax falls on owners.
  - Payroll taxes fall on workers.
  - Employer health premiums are treated as a privatized poll tax on covered workers.

## 2. The basic approach: observed baseline, modeled changes

The model does **not** simulate the current tax system. The current-law rate for each group is
simply DINA's estimate of what that group pays, tax by tax, as a share of its income (`E(g)`, §4).
Some of these components are close to direct observation. The individual income tax, for example,
comes from tax-return microdata. Others are allocations of aggregate receipts according to the
incidence conventions in §1, such as corporate, property, sales and payroll taxes. Either way, the
model takes the baseline as given. It never tries to reproduce the baseline from statutes, and
for a reform that changes nothing it returns the baseline unchanged.

The model's job is to estimate the **change** in each group's rate that a counterfactual policy
would cause, and apply that change to the baseline. This has several consequences:

- **The gap between statute and reality isn't modeled.** Everything that separates what the
  statutes would collect from what is actually paid is already in the baseline: deductions,
  credits, state taxes, avoidance, evasion and timing. Under a reform this gap is held fixed, in
  one of the ways described in §3.
- **Levels are pinned to the data; only changes depend on the model.** Mistakes in the model's
  simplified tax rules affect estimated changes, not the current-law levels.
- **Levers use the baseline's concepts.** Some levers are effective rates rather than statutory
  ones. The current corporate lever is the effective rate on C-corporation profits
  (210/1308 ≈ 16%), not the 21% statutory rate, and candidates' statutory proposals must be
  translated into it.
- **Revenue is the sum of changes.** Net revenue is the income-weighted sum of the group-level
  changes (§4).

This differs from a microsimulation model such as NBER TAXSIM or Tax-Calculator. Those models
compute current-law liabilities from the statutes on tax-unit records, and have to be calibrated
to observed totals. Here, the baseline matches the national accounts by construction, because
DINA's group totals add up to them.

## 3. Modeling changes: additive, multiplicative, or from scratch

Since only changes are modeled, each tax needs a rule for turning a policy change into a change
in the observed rate `τ_c(g)`. The workbook uses four rules, and each one treats the unmodeled
residual (§2) differently:

| Rule | Proposed rate | Unmodeled residual is held fixed… | Taxes |
|---|---|---|---|
| **Unchanged** | `τ_p = τ_c` | entirely | Property tax; payroll tax (apart from the health head tax) |
| **Additive** | `τ_p = τ_c + Δ(g)/y` | in amount (dollars per adult) | Individual income tax; the employer health head tax added to payroll |
| **Multiplicative** | `τ_p = τ_c × ratio` | in proportion to the tax | Corporate, estate, sales/excise, private health insurance |
| **From scratch** | `τ_p` computed directly; `τ_c = 0` | (no baseline to anchor on) | Wealth tax (including the FTT surcharge), national income tax, VAT |

- **Additive.** The individual income tax adds the change in statutory liability to the observed
  rate. That change is `[T(proposed) − T(current)]/y`, the bracket and preferential-treatment terms
  CE–CI, computed on the taxable-income excess curves (§5). The result is floored at zero. Credits,
  state income taxes and avoidance stay the same dollar amount when federal brackets change. The
  head tax adds `0.55 · healthtax(g)` to payroll.
- **Multiplicative.** These taxes have an aggregate lever but no per-group statutory computation.
  Each group's share of the tax is fixed by its incidence allocation, and the whole tax is scaled
  by the ratio of proposed to current aggregate:
  - Corporate: `K_p/K_c`, effective rate plus foreign-profit tax, on $1,308bn of profits.
  - Estate: `(top rate / 0.4) · (1 − avoidance)/(1 − 0.6)`.
  - Sales/excise and private health insurance: `(1 − reduction)`.
- **From scratch.** New taxes have a zero baseline, so the statutory computation is the whole
  estimate:
  - The wealth tax applies its schedule to the wealth excess curves, times `(1 − evasion)`.
  - The national income tax is `rate · nitaxbase/y`.
  - The VAT applies the formula in §6.

  These are the only places where the model's own assumptions determine a *level*, rather than a
  change. Formally, "from scratch" is the additive rule with `τ_c = 0`.
- **Crowd-out on top.** After their own adjustment, the individual income tax and the corporate tax
  are multiplied by `(1 − R − S)`. Here R and S are each group's effective rates from the new
  national income tax and the VAT (columns R and S, §6). So the income tax is first adjusted
  additively, then multiplicatively.

At current law every Δ is 0, every ratio is 1, and every new tax is 0, so a zero reform raises
exactly zero revenue. The authors' reasons for using each rule are among the open questions
([`questions.md`](questions.md) §13), as is whether fiscal income keeps the same relation to
economic income under reform (§14 there).

In the generic engine (`docs/architecture.md`), each rule is a reusable instrument:
- `Fixed`: unchanged.
- `UsIncomeTax`, and `ScheduleTax` with an observed baseline: additive.
- `CorporateTax`, `RateAndAvoidanceScaled` and `Reducible`: multiplicative.
- `ScheduleTax` and `FlatTax` without a baseline, and `ConsumptionTax`: from scratch.

For a country that already has a VAT, `ConsumptionTax` switches to the multiplicative rule. It
rescales the observed VAT incidence by `(v_p/(1+v_p)) / (v_c/(1+v_c))`.

## 4. Output

For each group *g* the model returns the effective tax rate under the **current** system,
`E(g) = effrate(g) + L(g)`, and under the **proposed** system, `Z(g) = Σ_taxes τ_tax(g)`.
Aggregate rates are income-weighted means, `Σ τ·n·y / Σ n·y` (= total tax / total income, row 137).

- **Net revenue** is `Z̄ − Ē`, as a share of national income (Z138).
- **Annual revenue**: Z138 × 17,546 × 1.038 (2018 NI in $bn, inflated to 2019; interface X8).
- **Ten-year revenue (2021–2030)** assumes 4% nominal growth: X8 × 1.04² × (1.04¹⁰ − 1)/0.04 (interface Y8).

**Health-insurance option** (interface C16). When it is "yes", `L(g) = healthtax(g)` is added to
current rates, and the proposed health component is `T = L·(1 − reduction)`. When it is "no", L = T = 0.

## 5. The bracket device

Every progressive schedule is evaluated from group-average **excesses**. Let `X_k(g)` be the
average over adults in *g* of max(base − t_k, 0), for thresholds t_0 < t_1 < …, and let r_k be
marginal rates. Then the average tax is

    tax(g) = Σ_k (r_k − r_{k−1}) · X_k(g),   r_{−1} = 0        (Numerics.bracketTax)

This is exact, because the excess is linear in the group average. It is the reason the data
carry excess columns rather than distributions.

## 6. Proposed rate by tax

Notation: `y` = income (C), `p` = proposed policy (interface column D), `c` = current policy (column C).

| Tax | Column | Formula |
|---|---|---|
| Wealth tax | Q | `(1 − evasion) · bracketTax(w_k, W_k(g)) / y`. `w_k` = proposed wealth-tax rates (G143–G156), plus a financial-transaction-tax surcharge of 0.05% (first bracket) / 0.1% (others) when FTT = yes. Thresholds: 0, 1m, 2.5m, 5m, 10m, 25m, 50m, 100m, 250m, 500m, 1bn, 2.5bn, 5bn, 10bn. |
| National income tax | R | `rate · nitaxbase / y` |
| VAT | S | `v/(1+v) · 0.4 · (0.75 · sales(g)/sales̄ + 0.25)`, where sales̄ = F137. The base is 40% of income, three quarters distributed like current sales taxes and one quarter proportionally to income. |
| Health insurance | T | `L · (1 − reduction)` |
| Estate tax | U | `(topRate · estate(g) / 0.4) · (1 − avoidance) / (1 − 0.6)` |
| Corporate tax | V | `(1 − R − S) · corp(g) · K_p / K_c`. `K_c = 1308·(c.rate + 8/1308)`, `K_p = 1308·(p.rate + f_p)`, with `f_p = 96/1308` if the multinationals switch differs from current, else 8/1308 (Q141–R144). |
| Individual income tax | W | `(1 − R − S) · max(ditax(g) + (CH + CI)/y, 0)` (see below) |
| Sales/excise | X | `sales(g) · (1 − reduction)` |
| Payroll | Y | `payroll(g) + 0.55 · healthtax(g)` if the employer head tax is on |
| Property | I | unchanged |

`(1 − R − S)` is the crowding-out of existing income-based taxes by new broad taxes.

**Individual income tax** (BB–CI). The switches `d`, `i`, `k` are 1 when the proposed treatment of
dividends, of dividend/corporate integration, and of capital gains *differs from current*
(AA141–AA143).

- `ΔB = bracketTax(p.rates − c.rates, taxinc_k(g))` is the extra tax from bracket changes (CE).
  Thresholds: 0, 77k, 165k, 315k, 400k, 600k, 1m, 2.5m, 5m, 10m, 25m, 50m.
- Shares of fiscal income: `s_kg = kg/agi`, `s_div = div/agi`.
- Retained earnings of closely held C-corporations: `RE(g) = (kg + div) · 166.6bn / Σ n(kg + div)` (BB).
- Federal corporate rates: `τ_c = 150/1308 + 8/1308` and `τ_p = p.rate − (c.rate − τ_c) + f_p` (Q142, R142).
- Gross-up: `GU = div · τ_c/(1 − τ_c) + RE/(1 − τ_c)` (CA).
- Bracket component CI:
  - `(1 − s_kg − s_div)·ΔB` if no switch.
  - `(1 − s_kg)·ΔB` if only d.
  - `(1 − s_div)·ΔB` if only k.
  - `ΔB` if d and k.
  - `ΔB·(1 + GU/agi)` if i and k.
  - `ΔB·(1 + GU/agi − s_kg)` if i without k.
- Preference component CH:
  - `kg·mtrextra` if k (BU).
  - plus `div·mtrextra` if d and not i (BV).
  - plus, if i: `div·((mtr − τ_p)/(1 − τ_c) − min(0.2, mtr)) + RE·(mtr − τ_p)/(1 − τ_c)` (BW).

## 7. Long-run behavioral block (CU–DO, EO–FD)

1. **Implied marginal tax rates.** With after-tax income `a = y(1 − τ)`, the slope to the next
   group is `s_i = (a_{i+1} − a_i)/(y_{i+1} − y_i)`. Beyond the last group, and before the first, the
   workbook compares to an empty row (0, 0). The implied rate is `m_i = min(1 − (s_i + s_{i−1})/2, 0.99)`
   (EO/EP). It is then smoothed by **7 passes of a centered 7-point moving average**, replicating
   3 edge values on each side (EQ…FD).
2. **Real response.** `DD = y · ((1 − m_p)/(1 − m_c))^0.25` (output elasticity, interface B68).
3. **Bargaining response.** `DE = DD · ((1 − m_p)/(1 − m_c))^(φ·0.5)`, where φ phases in linearly
   from 0 at P89 to 1 at P99 and above (bargaining elasticity, interface B69).
4. Income lost at the top through bargaining (`DF = DE − DD`) is redistributed in proportion to each
   group's shortfall relative to equitable growth since 1980: `DG = max(G_2018 · missingRaise, 0)` (sheet `growth`), and
   `DH = −DG · DF̄/DḠ`. Then `DI = DE + DH` and `DJ = DI · Z`.
5. Displayed: the % change in pre-tax income `DK = round(DI/y − 1, 3)`, the % change in after-tax
   income `DM = round(DI(1 − Z)/(y(1 − E)) − 1, 3)`, and the static `DN = (E − Z)/(1 − E)`.

## 8. Quirks of the 2020 workbook (reproduced faithfully, worth revisiting)

Open questions about these and other modeling choices, for Saez and Zucman, are collected in
[`questions.md`](questions.md).

1. **Wealth above $1bn.** Only the top-400 line has data above $1bn. For it, wealth above
   $2.5bn/$5bn/$10bn is imputed as 0.9/0.6/0.45 × wealth above $1bn (calculator!AQ22:AS22). For all
   other groups the excess above $2.5bn is zero, so bracket rates above $2.5bn only affect the
   top 400. (`Sz2019.top400WealthImputation`)
2. **Foreign profits counted twice in τ_p.** `R142 = R141 − (Q141 − Q142) + R143`. Q142 already
   includes Q143, so the proposed federal corporate rate counts both the current and the reformed
   foreign-profit tax. This only matters for the integration option.
3. **Unused levers.** The narrow payroll tax, broad payroll tax and AGI-base levers (interface rows
   12–14) appear in the interface but are not computed. They are kept in `SzPolicy` as natural
   extension points.
4. **Switches compare with current.** The dividend, capital-gains and integration switches mean
   "different from current law", not "on".
5. **Stale pasted curves.** The pasted plan curves EE ("book") and EF ("current"), with health shown
   as a tax, come from an older data version (|Δ| ≤ 2e-3, only in the top 0.1%). The other 12 pasted
   curves match the engine to ~1e-16. See `CalculatorBigParityTest`.
6. **Hard-coded rows in `calculator`.** The decile sheet `calculator` has hard-coded values in its
   top-400 row. The website used `calculatorbig`, which is what we port.
7. Several constants are hard-coded in formulas: 1308, 150, 8, 96, 166.6, 0.4, 0.75, 0.55, 0.6,
   17546, and 1.038. They are all fields of `SzParams` with their cell references.

## 9. Verification status

- `parity` tests (JVM and Scala.js) reproduce every computed column of the saved workbook state
  (the Obama plan) to ≤ 1e-12 relative: E, Q–Z, row-137 aggregates, Z138, interface W8–Y8, and
  DB–DO with DK137/DM137. They also reproduce 12 of the 14 pasted plan curves to machine
  precision.
- `PublishedRevenueTest` reproduces the published revenue figures, in % of national income,
  with health insurance as a tax:

  | Plan | Model | Published |
  |---|---|---|
  | Biden | 1.51 | 1.5 |
  | Buttigieg | 3.88 | 3.9 |
  | Sanders | 2.90 | 2.9 |
  | Warren | 3.24 | 3.2 |
  | Obama | 1.56 | 1.6 |
- The generic engine (`taxsim.model`, see `docs/architecture.md`) re-expresses this model with
  reusable instruments. It reproduces `SzModel` for every preset, tax and group (≤ 1e-12). It
  differs in one respect: current totals are the sum of components rather than the stored `effrate`
  (|Δ| ≤ 1e-8, float32 rounding), so that a zero reform raises exactly zero revenue.

## 10. Worked examples

Two taxes worked by hand, from `databig` to the final rate, for one group under Warren's plan.
`WorkedExamplesTest` (in `parity`) checks every number quoted here against the data and the
engine. If it fails, this section needs updating.

The group is **P99.997–99.998**: 1,938 adults just above $31.7m of income, with
`denom` = **$40,647,524**. All dollar amounts are averages per adult.

### 10.1 Wealth tax (rule: from scratch)

**Inputs** (`databig`):

| Column | Value |
|---|---|
| `hweal` = `wealth_0m` | $331,388,704 |
| `wealth_1m` | $330,798,560 |
| `wealth_50m` | $301,881,120 |
| `wealth_1000m` | $5,011,823 |
| excess above $2.5bn, $5bn, $10bn | 0 (imputed only for the top 400; §8, item 1) |

Average wealth is about 8.15 × `denom`.

**Marginal rates.** Warren's plan is 2% above $50m and 6% above $1bn. It also has the financial
transaction tax, modeled as a surcharge of 0.05% on the first bracket and 0.1% on the others. So
the marginal rates are 0.05% from $0, 0.1% from $1m, 2.1% from $50m and 6.1% from $1bn.

**Bracket device** (§5). Each rate *increase* is multiplied by the excess above its threshold:

| Threshold | Rate increase | Excess | Tax |
|---|---|---|---|
| $0 | 0.0005 | 331,388,704 | $165,694 |
| $1m | 0.0005 | 330,798,560 | $165,399 |
| $50m | 0.02 | 301,881,120 | $6,037,622 |
| $1bn | 0.04 | 5,011,823 | $200,473 |
| **Total** | | | **$6,569,189** |

**Evasion and normalization.** After evasion, $6,569,189 × (1 − 0.15) = $5,583,811. Dividing by
`denom` gives **Q = 0.13737**, about 13.0 points from the wealth tax and 0.7 points from the
financial transaction tax.

The current rate is 0, so all of it is a change. Nothing converts wealth to income explicitly:
dividing dollars by dollars applies the 8.15 ratio implicitly. Wealth, its distribution above each
threshold, income and the evasion rate are all held at their 2018 values.

### 10.2 Individual income tax (rule: additive)

**Inputs** (`databig`):

| Column | Value | Meaning |
|---|---|---|
| `ditax` | 0.19687 | observed federal + state income tax, share of `denom` (≈ $8.0m) |
| `agi` | $25,726,080 | fiscal income |
| `fikgi` | $13,170,995 | realized capital gains |
| `fidiv` | $1,819,416 | dividends |
| `mtrextra` | 0.167 | average federal marginal rate above 20% |
| `taxinc_315k` / `_400k` / `_600k` | $23,762,612 / $23,720,880 / $23,622,688 | taxable income above each threshold |

The ratios to `denom` are implicit: AGI is 63% of `denom` and taxable income is 59%. Capital
gains are 51% of AGI.

**What Warren changes.**
- **Brackets.** Current law is 32% / 35% / 37% from $315k / $400k / $600k. Warren's plan is 37% from
  $315k and 55% from $400k up.
- **Switches.** Dividends and capital gains are fully taxed, so `d` and `k` are on. Integration
  (`i`) is off.
- **No crowd-out.** There is no national income tax and no VAT, so `(1 − R − S)` = 1. The
  corporate changes don't reach column W, because integration is off.

**ΔB (CE).** The bracket device applied to the rate differences:

| Threshold | Δ rate increase | Excess | Tax |
|---|---|---|---|
| $315k | +0.05 | 23,762,612 | $1,188,131 |
| $400k | +0.15 | 23,720,880 | $3,558,132 |
| $600k | −0.02 | 23,622,688 | −$472,454 |
| **ΔB** | | | **$4,273,809** |

The −0.02 appears because current law rises from 35% to 37% at $600k, while Warren's rate stays
flat at 55%.

**CI.** Gains are `s_kg` = 0.512 of AGI and dividends are `s_div` = 0.071. With `d` and `k` on,
all taxable income faces the new brackets, so **CI = ΔB = $4,273,809**. With neither switch on, it
would be (1 − 0.512 − 0.071) × ΔB = $1,783,490.

**CH.** Taxing gains and dividends at the ordinary rate instead of the preferential rate adds
`mtrextra` on each:

| Component | Calculation | Amount |
|---|---|---|
| Capital gains | 13,170,995 × 0.167 | $2,199,556 |
| Dividends | 1,819,416 × 0.167 | $303,842 |
| **CH** | | **$2,503,399** |

**Assembling W.**

    W = (1 − R − S) · max( ditax + (CH + CI)/denom , 0 )
      = 1 · ( 0.19687 + 0.05411 + 0.00748 + 0.10514 )
      = 0.36360

| Term | Rate (share of `denom`) |
|---|---|
| Observed `ditax` | 0.19687 |
| Capital gains taxed as ordinary income | 0.05411 |
| Dividends taxed as ordinary income | 0.00748 |
| New brackets (ΔB) | 0.10514 |
| **W** | **0.36360** |

The income tax goes from 19.7% to 36.4% of income, about $8.0m to $14.8m per adult.

- **The baseline carries over.** The observed 19.7% includes state income tax, credits, deductions
  and avoidance, and it is carried over unchanged in dollars. That is the additive rule (§3).
- **Realizations are frozen.** The group is assumed to keep realizing $13.2m of gains per adult at
  the new rates. That drives about a third of the increase ([`questions.md`](questions.md) §14).
- **Crowd-out.** Under a plan with a national income tax, such as the "book" plan, the whole
  expression would be multiplied by `(1 − R − S)`.
