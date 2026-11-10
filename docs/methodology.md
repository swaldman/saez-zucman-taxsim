# The Saez–Zucman tax simulator: methodology as implemented

This document specifies the model behind [taxjusticenow.org](https://taxjusticenow.org) as it is
implemented in `reference/taxsimulator2.xlsx` (revised January 20, 2020), and as ported to
`core/src/taxsim/sz/`. Every formula is tied to the spreadsheet cells it comes from (sheet
`calculatorbig` unless noted; row 8 is the first group, rows 141–156 hold parameters).

Background sources: `reference/SZ2019Appendix.pdf` (data construction) and
`reference/taxsimsummary_v2.pdf` (the 2020 candidate plans).

## 1. Unit of analysis and data

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
  (§3). The remaining inputs are the national-income-tax base, the average ordinary marginal rate
  (`mtr`), the average marginal rate above 20% (`mtrextra`), and disposable cash income.
- **Incidence conventions** (Appendix §1.1):
  - 70% of consumption taxes fall on consumers (allocated following ITEP state profiles) and
    30% on factors of production, in proportion to factor income.
  - Corporate tax falls on owners of C-corporation equity.
  - Property tax falls on owners.
  - Payroll taxes fall on workers.
  - Employer health premiums are treated as a privatized poll tax on covered workers.

## 2. Output

For each group *g* the model returns the effective tax rate under the **current** system,
`E(g) = effrate(g) + L(g)`, and under the **proposed** system, `Z(g) = Σ_taxes τ_tax(g)`.
Aggregate rates are income-weighted means, `Σ τ·n·y / Σ n·y` (= total tax / total income, row 137).

- **Net revenue** is `Z̄ − Ē`, as a share of national income (Z138).
- **Annual revenue**: Z138 × 17,546 × 1.038 (2018 NI in $bn, inflated to 2019; interface X8).
- **Ten-year revenue (2021–2030)** assumes 4% nominal growth: X8 × 1.04² × (1.04¹⁰ − 1)/0.04 (interface Y8).

**Health-insurance option** (interface C16). When it is "yes", `L(g) = healthtax(g)` is added to
current rates, and the proposed health component is `T = L·(1 − reduction)`. When it is "no", L = T = 0.

## 3. The bracket device

Every progressive schedule is evaluated from group-average **excesses**. Let `X_k(g)` be the
average over adults in *g* of max(base − t_k, 0), for thresholds t_0 < t_1 < …, and let r_k be
marginal rates. Then the average tax is

    tax(g) = Σ_k (r_k − r_{k−1}) · X_k(g),   r_{−1} = 0        (Numerics.bracketTax)

This is exact, because the excess is linear in the group average. It is the reason the data
carry excess columns rather than distributions.

## 4. Proposed rate by tax

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

## 5. Long-run behavioral block (CU–DO, EO–FD)

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

## 6. Quirks of the 2020 workbook (reproduced faithfully, worth revisiting)

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

## 7. Verification status

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
