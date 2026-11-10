# Generic engine (`taxsim.model`)

The Saez–Zucman model is a *static incidence* model. It takes the current effective rate of each
tax, by income group. Then it recomputes each tax under a proposed system, either by computing it
from its base (new taxes) or by rescaling its observed incidence (existing taxes). `taxsim.model`
turns that idea into reusable parts. A country model is configuration, not code: groups, instruments,
levers and constants.

```
Model ── groups: Group(label, percentile, adults, income, vars, excess curves)
      ── instruments: Instrument (baseline rate, reformed rate, crowdedOutBy)
      ── schema: LeverSchema (sliders, toggles, bracket editors; for UIs and validation)
      ── currentPolicy / presets: Policy (lever id → number | flag | Schedule)
      ── macroData: national income, currency, budget window
Engine.run(model, proposed, options) → Outcome (rates by group × instrument, aggregates, revenue)
Behavioral.run(inputs, settings)      → long-run responses (optional)
```

## Key ideas

**Excess curves make brackets free.** Every progressive schedule (income tax, wealth tax, estate
tax) is evaluated as Σ_k (r_k − r_{k−1}) · X(t_k), where X(t) is the group-average excess of the
base over t. This is exact for any distribution within the group. `ExcessCurve` stores X at grid
thresholds and interpolates between them:
- It uses a Pareto (log-log) rule, which is exact for Pareto tails.
- It is clamped to bounds that every distribution satisfies: below the chord, by convexity, and
  above X(t_k) − (t − t_k), because the slope is ≥ −1.

So users can move thresholds anywhere. For example, "50% above $3m" uses
`Schedule.withMinimumRateAbove`.

**The difference method preserves calibration.** A computed tax is applied as
`reformed = observed baseline + T(proposed) − T(current)`. A zero reform therefore reproduces the
observed incidence exactly. This is true even when the model's formula for T is only an
approximation of the actual tax code.

**Relative crowd-out.** An instrument can declare that broad taxes (VAT, a national income tax) shrink
its base. Its reformed rate is multiplied by `(1 − Σ proposed broad rates) / (1 − Σ baseline broad
rates)`. With no current broad taxes (the US) this is the Saez–Zucman factor `(1 − NIT − VAT)`.
In a country with an existing VAT, a zero reform leaves everything unchanged.

**Baseline = sum of instruments.** Aggregate current rates are the sums of instrument baselines, so
revenue is internally consistent. A zero reform raises exactly zero. The 2020 workbook instead used
a stored total that differs from the sum of its stored components by float32 rounding (≤ 1e-8).

## Building blocks (`Instruments`)

| Instrument | Use |
|---|---|
| `Fixed` | Tax kept at observed incidence (property taxes). |
| `Reducible` | Observed incidence × (1 − reduction). Optionally shown only when an option is on (US private health insurance). |
| `RateAndAvoidanceScaled` | Observed incidence scaled by the top rate and compliance (estate tax). |
| `CorporateTax` | Observed incidence scaled by total corporate revenue, including the tax on foreign profits. |
| `Payroll` | Observed social contributions, plus an optional head tax. |
| `FlatTax` | Rate × per-adult base (national income tax, extra social contributions). |
| `ScheduleTax` | Brackets on an excess curve, with evasion and an optional on/off switch (wealth tax, FTT surcharge, generic income tax). |
| `ConsumptionTax` | VAT/GST. Either from a formula (base share of income; consumer vs factor incidence along a consumption profile) or by rescaling an observed VAT incidence by v/(1+v). |
| `us.UsIncomeTax` | US-specific: preferential capital gains and dividends, and dividend/corporate integration (Saez–Zucman). |

## Models

- `taxsim.data.sz2019.Sz2019.model`: US 2018, the 2020 workbook re-expressed with the building
  blocks (`taxsim.data.us.UsModel`). `GenericModelEquivalenceTest` shows it reproduces the faithful
  port `taxsim.sz.SzModel` for every preset, every tax and every group (≤ 1e-12). `SzModel` itself
  matches the workbook cell by cell.
- `VatCountryTest` (core tests): a synthetic European-style country with an existing 20% VAT,
  social contributions, a progressive income tax and an optional wealth tax.

## Adding a country

1. Build groups (ideally WID-style g-percentiles plus top lines). Each group needs adults, average
   pre-tax income, observed effective rates by tax, and excess curves for the bases you want
   brackets on.
2. Choose instruments for its tax mix. Add a new `Instrument` only for genuinely country-specific
   logic, as `UsIncomeTax` does for the US.
3. Write a `LeverSchema`, the current `Policy` and presets. `schema.validate` checks them.
4. Add tests: a zero reform gives zero revenue, aggregates match national revenue statistics by tax,
   and rates are close to published incidence studies.
