# QYNEX ECOMMERCE PLUS

# TEMPLATE PROMPT — MINIMAL

## Ultra-Clean Product-First Premium Ecommerce Experience

---

# 01. ROLE

Act as a multidisciplinary senior digital commerce team composed of:

- Senior Staff Frontend Engineer.
- Senior UI/UX Designer.
- Senior Ecommerce UX Specialist.
- Senior Product Designer.
- Design Systems Architect.
- Art Director.
- Motion Designer.
- Interaction Designer.
- Conversion Rate Optimization Specialist.
- Responsive Web Designer.
- Accessibility Specialist.
- Performance Engineer.
- Visual Brand Designer.

You are working directly inside the existing production-oriented ecommerce SaaS:

**QYNEX ECOMMERCE PLUS**

Your task is to implement the official:

# MINIMAL TEMPLATE

Template identifier:

```text
MINIMAL
```

This is not:

```text
CLASSIC
with
fewer components
```

It is not:

```text
CLASSIC
+
more whitespace
```

It is not:

```text
generic ecommerce
+
white background
```

MINIMAL must feel like an entirely different ecommerce product.

It must deliver a refined, highly intentional, product-first shopping experience where:

```text
PRODUCT
IMAGE
TYPOGRAPHY
SPACE
CLARITY
```

are more important than decorative interface elements.

The storefront must feel expensive because of its restraint, not because of visual effects.

---

# 02. CORE DESIGN MISSION

MINIMAL exists to make the product itself feel valuable.

The customer should feel:

```text
CALM
FOCUS
QUALITY
PRECISION
CONFIDENCE
DESIRE
```

The interface must disappear behind the merchandise.

The experience should communicate:

> "The product is important enough that the interface does not need to shout."

MINIMAL should be especially effective for:

- Technology products.
- Design products.
- Premium accessories.
- Lifestyle products.
- Furniture.
- Home decoration.
- Handmade products.
- Photography-driven brands.
- Modern retail.
- Premium consumer goods.
- Modern independent brands.
- Products with strong visual identity.

---

# 03. ABSOLUTE DESIGN PRINCIPLE

Every element must justify its presence.

Ask constantly:

```text
Does the customer need this?
Does this improve understanding?
Does this improve product discovery?
Does this improve conversion?
Does this improve trust?
```

If the answer is no:

**remove it.**

Avoid filling space simply because space exists.

Whitespace is an active design element.

It must be used intentionally to:

- Separate ideas.
- Establish hierarchy.
- Emphasize products.
- Increase perceived quality.
- Reduce cognitive load.
- Guide visual attention.

---

# 04. BUSINESS LOGIC IS AUTHORITATIVE

MINIMAL controls:

```text
PRESENTATION
LAYOUT
TYPOGRAPHY
SPACING
VISUAL HIERARCHY
MOTION
INTERACTION DESIGN
RESPONSIVE BEHAVIOR
```

The existing Qynex ecommerce engine controls:

```text
PRODUCTS
CATALOG
CATEGORIES
BRANDS
STOCK
PRICES
DISCOUNTS
PROMOTIONS
VARIANTS
CART
CHECKOUT
PAYMENTS
ORDERS
SHIPPING
AUTHENTICATION
CUSTOMERS
TENANTS
NOTIFICATIONS
AI ASSISTANT
APIs
BUSINESS RULES
```

Never replace existing business logic.

Never create:

```text
another cart
another checkout
another payment flow
another authentication flow
another product service
another order service
another catalog engine
```

Reuse the existing implementation.

The template is a presentation layer.

---

# 05. FIRST ACTION — AUDIT THE EXISTING APPLICATION

Before modifying code, inspect the existing storefront thoroughly.

At minimum inspect:

```text
front/tienda/
front/tienda/js/store/
store-shell.js
store-api.js
```

Inspect every relevant page:

```text
tienda/index.html
tienda/producto.html
tienda/carrito.html
tienda/checkout.html
tienda/cuenta/login.html
tienda/cuenta/registro.html
tienda/cuenta/pedidos.html
tienda/no-disponible.html
```

Inspect:

- Product rendering.
- Product cards.
- Product detail.
- Product images.
- Categories.
- Brands.
- Search.
- Filters.
- Sorting.
- Cart.
- Checkout.
- Payments.
- Customer authentication.
- Customer registration.
- Orders.
- Tenant configuration.
- StoreTemplate resolution.
- Existing CSS.
- Existing JavaScript.
- Existing responsive rules.
- Existing events.
- Existing DOM selectors.
- Existing reusable components.

Determine what can be safely restyled and what requires structural adaptation.

Do not guess.

Read the implementation first.

---

# 06. MINIMAL VISUAL PHILOSOPHY

MINIMAL should feel:

- Quiet.
- Premium.
- Precise.
- Modern.
- Spacious.
- Product-focused.
- Editorial without becoming a magazine.
- Sophisticated without becoming luxury-themed.
- Simple without appearing unfinished.

Avoid:

- Excessive cards.
- Boxes around everything.
- Excessive borders.
- Heavy shadows.
- Random gradients.
- Glassmorphism.
- Giant floating widgets.
- Decorative blobs.
- Unnecessary icons.
- Excessive badges.
- Huge amounts of promotional text.
- Aggressive CTA repetition.
- Template-marketplace styling.
- Generic Bootstrap structure.
- Artificial "AI website" styling.

The page should breathe.

---

# 07. MINIMAL ≠ LUXURY

Do not confuse MINIMAL with LUXURY.

MINIMAL means:

```text
FUNCTION
CLARITY
PRODUCT
SPACE
MODERNITY
```

LUXURY emphasizes:

```text
EXCLUSIVITY
PRESTIGE
ELEGANCE
CEREMONY
```

MINIMAL may use modern sans-serif typography and highly functional layouts.

It should feel contemporary and effortless.

It should not automatically look like a jewelry or perfume boutique.

---

# 08. DESIGN PSYCHOLOGY

Customer journey:

```text
SEE
↓
FOCUS
↓
UNDERSTAND
↓
EXPLORE
↓
DESIRE
↓
SELECT
↓
BUY
```

The interface should minimize visual competition.

When displaying a product:

```text
IMAGE
↓
NAME
↓
PRICE
↓
ESSENTIAL OPTIONS
↓
PURCHASE
```

The customer should never need to decode the interface.

---

# 09. DESIGN SYSTEM

Create a dedicated MINIMAL design system.

Use controlled variables such as:

```css
--minimal-primary
--minimal-secondary
--minimal-accent

--minimal-background
--minimal-surface
--minimal-surface-subtle

--minimal-text
--minimal-text-muted

--minimal-border
--minimal-border-subtle

--minimal-success
--minimal-warning
--minimal-error

--minimal-container
--minimal-content-width

--minimal-radius-sm
--minimal-radius-md

--minimal-space-xs
--minimal-space-sm
--minimal-space-md
--minimal-space-lg
--minimal-space-xl
--minimal-space-2xl

--minimal-transition-fast
--minimal-transition-normal
--minimal-transition-slow
```

Prefer a reduced token set.

Do not create unnecessary visual complexity.

---

# 10. COLOR STRATEGY

MINIMAL should generally rely on:

```text
NEUTRAL BACKGROUND
+
STRONG TEXT
+
TENANT BRAND COLOR
```

The tenant primary color should be treated as an accent rather than painting the whole interface.

Possible use:

- Primary CTA.
- Active navigation.
- Small focus states.
- Interactive links.
- Selected variant.
- Progress indicators.

Avoid flooding:

- Entire headers.
- Huge backgrounds.
- Every button.
- Every icon.
- Every badge.

with the brand color.

If tenant branding requires stronger color usage, integrate it carefully while preserving MINIMAL's visual philosophy.

---

# 11. TYPOGRAPHY

Typography is one of MINIMAL's main design elements.

Create strong hierarchy using relatively few font sizes.

Possible hierarchy:

```text
DISPLAY
H1
H2
H3
BODY
SMALL
CAPTION
PRICE
CTA
```

Use:

- Generous line-height.
- Strong spacing between text blocks.
- Clear weight hierarchy.
- High readability.
- Restrained uppercase.
- Precise letter spacing.

Avoid:

- Too many font weights.
- Too many text sizes.
- Decorative fonts everywhere.
- Giant marketing text without purpose.

Large typography may be used when supported by content, especially in hero or category presentation.

---

# 12. SPACING SYSTEM

Whitespace is critical.

MINIMAL should use more vertical and horizontal spacing than CLASSIC.

Desktop sections should have generous breathing room.

Example rhythm:

```text
SECTION
        ↓
large intentional space
        ↓
CONTENT
        ↓
large intentional space
        ↓
NEXT SECTION
```

Do not compress the homepage into a dense catalog.

Do not artificially increase page length either.

Whitespace must support hierarchy.

---

# 13. GRID PHILOSOPHY

Use clean, precise grids.

Desktop product grids may use:

```text
2
3
4
```

columns depending on:

- Product imagery.
- Screen size.
- Available data.
- Product type.

Avoid excessive product density.

For MINIMAL:

**fewer larger product cards are often better than many tiny cards.**

Ultra-wide screens should not simply add endless columns.

Constrain the visual experience.

---

# 14. HEADER — MINIMAL SIGNATURE

The header must be substantially different from CLASSIC.

Possible desktop composition:

```text
┌──────────────────────────────────────────────────────────────┐
│ LOGO            NAVIGATION              SEARCH  ACCOUNT CART │
└──────────────────────────────────────────────────────────────┘
```

Or, depending on available data:

```text
┌──────────────────────────────────────────────────────────────┐
│ MENU                 LOGO                   SEARCH    CART    │
└──────────────────────────────────────────────────────────────┘
```

The exact composition should reflect the existing storefront capabilities.

Requirements:

- Low visual height.
- Clear logo.
- Minimal navigation.
- Search accessible but not visually dominant.
- Account accessible.
- Cart always discoverable.
- Cart quantity visible when useful.
- Strong responsive behavior.

Avoid a heavy two-row retail header unless real data requires it.

---

# 15. HEADER INTERACTION

The header should feel almost invisible until required.

Possible behavior:

```text
TOP OF PAGE
↓
transparent / integrated header where contrast allows

SCROLL
↓
solid compact header
```

or:

```text
normal header
↓
slightly reduced spacing
↓
sticky minimal navigation
```

Never compromise readability.

Never put white text over imagery when contrast is insufficient.

If a transparent header is unsafe:

use a solid surface.

---

# 16. SEARCH EXPERIENCE

Search must remain powerful even though visually understated.

Default state may be:

```text
SEARCH ICON
```

and expand elegantly into:

```text
────────────────────────────────────────
Search products
────────────────────────────────────────
```

Desktop search can open:

- Inline expansion.
- Header overlay.
- Focus panel.
- Search modal.

Choose what best integrates with the existing architecture.

Search states:

```text
DEFAULT
OPEN
FOCUS
TYPING
LOADING
RESULTS
NO RESULTS
ERROR
```

If existing APIs support suggestions:

show them with minimal styling.

Example:

```text
SEARCH

Product name                           S/120
Product name                            S/90

Category
Category
```

No unnecessary card boxes around every result.

---

# 17. MOBILE HEADER

Mobile is extremely important.

Preferred concept:

```text
┌─────────────────────────────┐
│ MENU       LOGO     SEARCH CART │
└─────────────────────────────┘
```

or an equivalent minimal composition.

Requirements:

- Compact.
- Touch-friendly.
- Clear hierarchy.
- No tiny tap targets.
- No desktop navigation squeezed into mobile.
- Cart accessible.
- Search accessible.
- Menu accessible.

Minimum touch targets:

```text
44px × 44px
```

---

# 18. MOBILE MENU

Use a clean drawer or full-height navigation panel.

Possible layout:

```text
MENU                                      ×

New
Categories
Collections
Brands

────────────────────────

Account
Orders
```

Only show entries supported by existing functionality/data.

Motion:

```text
small translate
+
opacity
```

No excessive elastic animation.

---

# 19. HERO PHILOSOPHY

MINIMAL should not automatically use a conventional commercial hero carousel.

Prefer:

```text
ONE STRONG VISUAL
```

over:

```text
constant rotating advertising
```

When real banner content exists, intelligently determine whether:

- Single hero.
- Slow crossfade.
- Minimal slider.
- Split hero.
- Full-bleed image.

best supports the tenant.

Do not force carousel arrows, dots and multiple promotional elements when unnecessary.

---

# 20. MINIMAL HERO — DESKTOP

A signature MINIMAL hero could look like:

```text
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                                                             │
│                       LARGE IMAGE                           │
│                                                             │
│                                                             │
│     Headline                                                │
│     Short supporting text                                   │
│     Explore →                                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

Or:

```text
┌────────────────────────┬────────────────────────────────────┐
│                        │                                    │
│  HEADLINE              │                                    │
│  Short text            │           PRODUCT IMAGE            │
│                        │                                    │
│  Explore →             │                                    │
│                        │                                    │
└────────────────────────┴────────────────────────────────────┘
```

Use real data only.

---

# 21. HERO MOTION

MINIMAL motion should be subtle and precise.

Preferred:

- Fade.
- Crossfade.
- Small translate.
- Slow opacity transition.
- Subtle image scale.
- Elegant content reveal.

Avoid:

- Aggressive zoom.
- Bounce.
- Flashing.
- Fast auto-rotation.
- Huge parallax movement.
- Dramatic 3D effects.
- Particle effects.

Motion should almost disappear into the experience.

---

# 22. NO-BANNER BEHAVIOR

If there are no real banners:

**do not create fake banners.**

Instead transition directly into meaningful content.

Possible opening:

```text
HEADER
↓
FEATURED COLLECTION
↓
PRODUCTS
```

or:

```text
HEADER
↓
VISUAL CATEGORIES
↓
FEATURED PRODUCT
```

The lack of a hero should appear intentional.

---

# 23. HOMEPAGE ARCHITECTURE

MINIMAL should not use the same homepage rhythm as CLASSIC.

Possible architecture:

```text
MINIMAL HEADER
↓
PRIMARY VISUAL
↓
LARGE VISUAL CATEGORY SECTION
↓
CURATED PRODUCT GRID
↓
FEATURED PRODUCT STORY
↓
SECONDARY COLLECTION
↓
PRODUCT RAIL
↓
BRAND / STORE MESSAGE
↓
MINIMAL FOOTER
```

Only render sections supported by real data.

Do not turn the homepage into:

```text
Featured Products
New Products
Popular Products
Offers
Brands
Products
Products
Products
```

with identical card grids.

---

# 24. SECTION COMPOSITION

Every section should have its own visual rhythm.

Use combinations such as:

```text
FULL-BLEED IMAGE
```

```text
IMAGE + TEXT
```

```text
TEXT + IMAGE
```

```text
LARGE PRODUCT GRID
```

```text
EDITORIAL FEATURE
```

```text
HORIZONTAL PRODUCT RAIL
```

```text
VISUAL CATEGORY GRID
```

Avoid excessive section backgrounds.

Prefer space over boxes.

---

# 25. CATEGORY DISCOVERY

Categories should feel visual and curated.

Possible desktop:

```text
┌─────────────────────────┬─────────────────────────┐
│                         │                         │
│       CATEGORY A        │       CATEGORY B        │
│       large image       │       large image       │
│                         │                         │
└─────────────────────────┴─────────────────────────┘
```

Or:

```text
IMAGE
Category name
```

with minimal supporting UI.

On mobile:

use horizontal scroll where appropriate.

---

# 26. PRODUCT DISCOVERY PRINCIPLE

MINIMAL is selective.

The page should communicate:

```text
CURATED
```

rather than:

```text
OVERLOADED
```

Use:

- Featured products.
- New products.
- Selected collections.
- Product rails.
- Visual categories.
- Brands when useful.
- Promotions only when real.

Do not show every possible discovery mechanism simultaneously.

---

# 27. PRODUCT CARD — MINIMAL SIGNATURE

The product card should barely feel like a "card."

Avoid obvious container boxes.

Preferred structure:

```text
┌──────────────────────────────┐
│                              │
│                              │
│         PRODUCT IMAGE        │
│                              │
│                              │
└──────────────────────────────┘

Brand
Product name

S/ XXX.XX
```

Possible secondary actions may appear only where necessary.

Avoid permanently visible large buttons unless conversion analysis or product type justifies them.

---

# 28. PRODUCT CARD INFORMATION PRIORITY

Display only real, useful data.

Priority:

```text
1. IMAGE
2. PRODUCT NAME
3. PRICE
4. BRAND if useful
5. PRODUCT STATE
6. VARIANT information when useful
7. CTA
```

Do not overload cards with:

- SKU.
- Long descriptions.
- Too many badges.
- Excessive stock messaging.
- Multiple buttons.

unless required by real use cases.

---

# 29. PRODUCT CARD HOVER

Desktop hover should feel refined.

Possible sequence:

```text
PRODUCT IMAGE
↓
very subtle scale
```

If secondary image exists:

```text
PRIMARY IMAGE
↓
CROSSFADE
↓
SECONDARY IMAGE
```

Product information may shift subtly.

CTA may appear elegantly if appropriate.

Avoid:

```text
card jumps
large shadow
button flies upward
badges animate
multiple icons appear
```

Keep the interaction quiet.

---

# 30. PRODUCT CARD MOBILE

Do not depend on hover.

Mobile card should immediately expose enough information to shop.

Prioritize:

```text
IMAGE
NAME
PRICE
```

Use tap for product detail.

Add-to-cart may remain visible if the existing product structure supports direct purchase safely.

For variant-heavy products, opening product detail may be preferable.

Do not create new business behavior.

---

# 31. PRODUCT IMAGE SYSTEM

Photography is central to MINIMAL.

Requirements:

- Maintain aspect ratio.
- Never stretch.
- Avoid layout shifts.
- Use object-fit intelligently.
- Use responsive images where possible.
- Lazy-load non-critical imagery.
- Preserve sufficient resolution.

Product image areas should feel large.

Avoid cramming text directly against imagery.

---

# 32. IMAGE TRANSITIONS

If multiple product images are available:

use:

```text
crossfade
```

or a subtle directional transition.

Image changes should feel smooth.

Do not create fast slideshows inside every product card.

---

# 33. PRODUCT CAROUSELS

Use horizontal product rails sparingly.

MINIMAL product rail:

```text
←

PRODUCT        PRODUCT        PRODUCT        PRODUCT

                                                        →
```

Large spacing.

Large imagery.

Small, restrained controls.

On mobile:

```text
PRODUCT       PRODUCT
      swipe →
```

Allow partial visibility of the next product when useful to signal horizontal interaction.

---

# 34. CATALOG PAGE PHILOSOPHY

The catalog should feel clean, not dense.

Desktop concept:

```text
CATEGORY / TITLE

Description if real

FILTERS                         SORT

────────────────────────────────────

PRODUCT       PRODUCT       PRODUCT

PRODUCT       PRODUCT       PRODUCT
```

Filters should remain accessible without dominating the page.

---

# 35. DESKTOP FILTER EXPERIENCE

Possible strategies:

### Option A

Persistent minimal sidebar.

### Option B

Collapsible filter panel.

### Option C

Horizontal filter controls.

Choose according to existing functionality and catalog complexity.

Do not sacrifice usability simply to make the interface "minimal."

A store with complex filters still requires good filters.

---

# 36. MOBILE FILTER EXPERIENCE

Use:

```text
[FILTER]         [SORT]
```

Prefer a clean bottom sheet or drawer.

Possible structure:

```text
Filters                                   ×

Category

Brand

Price

Availability


Clear                         Apply
```

Requirements:

- Touch accessible.
- Keyboard accessible.
- Scrollable.
- Clear close action.
- Existing filter logic only.
- No duplicate state.

---

# 37. SORT EXPERIENCE

Sorting should be visually understated.

Possible:

```text
Sort: Recommended ▾
```

or:

```text
SORT
```

opening a compact selection panel.

Do not waste large amounts of screen space on the control.

---

# 38. PRODUCT DETAIL — DESIGN PRIORITY

The product detail page is one of MINIMAL's defining experiences.

It should feel like a digital product showroom.

Desktop concept:

```text
┌────────────────────────────────┬───────────────────────────┐
│                                │                           │
│                                │ PRODUCT NAME              │
│                                │                           │
│       LARGE PRODUCT            │ PRICE                     │
│       PHOTOGRAPHY              │                           │
│                                │ VARIANTS                  │
│                                │                           │
│                                │ QUANTITY                  │
│                                │                           │
│                                │ ADD TO CART               │
│                                │                           │
└────────────────────────────────┴───────────────────────────┘
```

Use a strong image-to-information ratio.

---

# 39. PRODUCT GALLERY — MINIMAL

Avoid cluttered thumbnail galleries.

Possible desktop solution:

```text
IMAGE
IMAGE
IMAGE
```

in a vertically flowing gallery.

Or:

```text
THUMBNAILS
|
LARGE IMAGE
```

depending on the existing image system.

The selected implementation should make photography feel premium.

Support existing capabilities for:

- Thumbnails.
- Swipe.
- Zoom.
- Fullscreen.
- Keyboard navigation.

Do not add unsupported gallery logic.

---

# 40. PRODUCT GALLERY MOBILE

Mobile should prioritize imagery.

Example:

```text
┌─────────────────────────────┐
│                             │
│                             │
│        PRODUCT IMAGE        │
│                             │
│                             │
└─────────────────────────────┘

             1 / 4
```

Use swipe where supported.

Avoid tiny thumbnails occupying valuable mobile space unless necessary.

---

# 41. PRODUCT INFORMATION HIERARCHY

Immediate sequence:

```text
PRODUCT NAME
↓
PRICE
↓
SHORT ESSENTIAL INFORMATION
↓
VARIANTS
↓
STOCK / AVAILABILITY
↓
QUANTITY
↓
PURCHASE
```

Long descriptions and secondary information should appear later.

The purchase decision should remain obvious.

---

# 42. PRODUCT VARIANTS

Variants should be visually clean.

Colors:

```text
○ ○ ○ ○
```

Sizes:

```text
XS   S   M   L   XL
```

or appropriate existing values.

States:

```text
default
hover
selected
disabled
unavailable
focus
```

Do not communicate availability using color only.

---

# 43. PRICE PRESENTATION

Price must be immediately identifiable without feeling promotional.

Possible:

```text
S/ 249.00
```

Previous price:

```text
S/ 299.00
```

Discount only if real.

Avoid giant red promotional labels unless tenant data explicitly supports promotional styling.

---

# 44. ADD TO CART

Primary CTA should be visually strong because most surrounding UI is restrained.

Possible:

```text
[ ADD TO CART ]
```

or:

```text
ADD TO CART →
```

depending on the final visual system.

After adding:

```text
ADD TO CART
↓
ADDED ✓
```

Provide immediate feedback.

Use existing cart state.

---

# 45. BUY NOW

If existing functionality supports Buy Now:

make it available without competing destructively with Add to Cart.

Possible hierarchy:

```text
[ ADD TO CART ]

Buy now
```

or the inverse depending on existing business flow.

Do not invent Buy Now logic if it does not exist.

---

# 46. STICKY PURCHASE EXPERIENCE

On long mobile product pages, consider:

```text
┌──────────────────────────────────┐
│ S/249                  ADD TO CART │
└──────────────────────────────────┘
```

Only if technically appropriate.

Respect:

- Safe-area insets.
- Mobile browser UI.
- Existing cart logic.
- Accessibility.
- Product option requirements.

Do not allow users to purchase without selecting required variants.

---

# 47. DESCRIPTION EXPERIENCE

Product descriptions should have excellent typography.

Avoid placing large descriptions inside heavy cards.

Use:

```text
Description
──────────────────

Readable paragraph text...
```

or progressive disclosure where appropriate.

Possible sections:

```text
Details
Specifications
Shipping
Additional information
```

only when real data exists.

---

# 48. CART — MINIMAL PHILOSOPHY

The cart should feel almost editorial.

Desktop:

```text
YOUR CART

Product                                         Price
────────────────────────────────────────────────────

IMAGE     Product name
          Variant
          Quantity                             S/120

────────────────────────────────────────────────────


                                      Subtotal   S/120

                                      CHECKOUT →
```

Reduce boxes and containers.

Use alignment and spacing instead.

---

# 49. CART ITEM INTERACTIONS

Quantity controls should be simple:

```text
−   1   +
```

Remove action should be visually secondary but accessible.

When updating:

- Avoid full-page reload feeling.
- Show localized loading.
- Update subtotal smoothly.
- Keep focus understandable.
- Preserve existing calculations.

---

# 50. EMPTY CART

Do not create a generic empty card.

Possible:

```text
Your cart is empty.

Discover something worth keeping.

Explore products →
```

Use store-safe language.

Do not invent promotions.

Keep the empty state spacious and useful.

---

# 51. CHECKOUT — MINIMAL PHILOSOPHY

Checkout must be extremely clean.

Goal:

```text
NO DISTRACTIONS
```

Desktop concept:

```text
CHECKOUT

Customer information            Order summary

Address                          Product
                                 Product
Shipping
                                 Subtotal
Payment                          Shipping
                                 Total

[ PLACE ORDER ]
```

No marketing banners.

No animated promotional blocks.

No unnecessary navigation.

---

# 52. CHECKOUT PROGRESS

If the existing checkout has steps, present progress minimally.

Example:

```text
Information  ─────  Delivery  ─────  Payment
```

or:

```text
01 Information
02 Delivery
03 Payment
```

Do not invent multiple steps if checkout is currently single-page.

Match existing business flow.

---

# 53. CHECKOUT FORMS

Inputs should feel premium but simple.

Possible:

```text
Email
────────────────────────────

Phone
────────────────────────────

Address
────────────────────────────
```

or subtle bordered inputs.

Requirements:

- Visible labels.
- Excellent focus states.
- Validation.
- Error messaging.
- Autofill compatibility.
- Mobile keyboard optimization.
- Appropriate input types.

Never rely solely on placeholder text.

---

# 54. PAYMENT AREA

Payment options must use the existing system.

Show:

- Existing payment method.
- Existing instructions.
- Existing amount.
- Existing payment status.

Do not create fake payment providers.

Do not visually imply a payment is secure unless supported by actual implementation/context.

---

# 55. ORDER CONFIRMATION

After successful purchase:

create a calm confirmation experience.

Possible:

```text
Order confirmed.

Order #1234

We've received your order.

View order →
```

Only show messaging supported by existing order flow.

No confetti.

No excessive celebration animation.

MINIMAL should remain restrained.

---

# 56. LOGIN PAGE

Login should feel nearly standalone.

Desktop example:

```text
                        LOGO

                      Welcome back

                      Email
                      ───────────────

                      Password
                      ───────────────

                      [ SIGN IN ]

                      Create account
```

Use existing fields only.

Large whitespace.

Minimal distraction.

---

# 57. REGISTRATION PAGE

Apply the same visual language.

Do not invent required fields.

Support:

- Validation.
- Error.
- Loading.
- Success.
- Password visibility if existing.
- Password confirmation if existing.

Keep the form readable and focused.

---

# 58. CUSTOMER ACCOUNT

For:

```text
tienda/cuenta/pedidos.html
```

avoid dashboard-like visual complexity.

Prefer:

```text
MY ORDERS

#1042
12 Aug 2026

Delivered

S/240.00                       View →
```

with clean separators.

Only show real order information.

---

# 59. ORDER STATES

Use clear text labels.

For example:

```text
Pending
Paid
Processing
Shipped
Delivered
Cancelled
```

only according to existing backend states.

Do not invent status mappings without inspecting the application.

Status should not depend on color alone.

---

# 60. STORE UNAVAILABLE PAGE

For:

```text
tienda/no-disponible.html
```

create a simple, polished experience.

Example hierarchy:

```text
LOGO

Store temporarily unavailable

Supporting message from existing state if available.

Return / Retry
```

Never display:

- Stack traces.
- Tenant IDs.
- Internal exceptions.
- Database information.
- API errors.
- Infrastructure details.

---

# 61. LOADING SYSTEM

Loading should be quiet and localized.

Use skeletons matching actual structures.

Example product:

```text
████████████████████

████████
████
```

Avoid giant spinners.

Avoid full-page loading overlays when unnecessary.

Critical content may load first.

Secondary content should load progressively.

---

# 62. SKELETON DESIGN

MINIMAL skeletons should be subtle.

Do not make them visually louder than real content.

Use:

- Neutral surfaces.
- Minimal animation.
- Correct dimensions.
- Stable aspect ratios.

Respect reduced motion.

---

# 63. EMPTY STATES

Empty states should be concise.

Pattern:

```text
Title

Short helpful explanation.

Action
```

Avoid giant illustrations unless an actual visual asset exists and fits the brand.

---

# 64. ERROR STATES

Errors must be understandable.

Do not display raw API messages by default.

Possible:

```text
We couldn't load the products.

Try again
```

Technical details belong in internal logging, not customer UI.

---

# 65. SUCCESS STATES

Success feedback should be subtle:

- Small check icon.
- Text.
- Short transition.
- Toast where existing architecture supports it.
- Inline confirmation.

Avoid celebratory explosion animations.

---

# 66. MOTION LANGUAGE

MINIMAL has its own motion philosophy:

```text
QUIET
SLOW-ENOUGH
CONTROLLED
PRECISE
PURPOSEFUL
```

Preferred motion:

- Fade.
- Crossfade.
- Small translate.
- Subtle scale.
- Image reveal.
- Smooth drawer.
- Controlled opacity change.
- Gentle sticky transition.

---

# 67. MOTION TIMING

Interactions should generally feel slightly slower and smoother than SPORT or URBAN.

Typical conceptual ranges:

```text
FAST UI FEEDBACK
150–200ms

NORMAL TRANSITION
200–350ms

VISUAL CROSSFADE
350–600ms
```

Do not blindly apply fixed values everywhere.

Use the timing appropriate to the interaction.

---

# 68. SCROLL REVEAL

Use sparingly.

Possible:

```text
opacity 0
translateY(12px)

↓

opacity 1
translateY(0)
```

Do not delay critical content.

Do not animate every product card individually with long stagger sequences.

The customer should never wait for products to become visible.

---

# 69. IMAGE REVEAL

Large visual sections may use:

- Fade.
- Clip reveal.
- Gentle scale.
- Crossfade.

Only if performant.

Avoid complex masking effects requiring heavy dependencies.

---

# 70. PARALLAX

Parallax is optional.

If used:

- Extremely subtle.
- Only in large imagery.
- Disable for reduced motion.
- Avoid scroll jank.
- Never hijack scrolling.

MINIMAL does not need parallax to feel premium.

---

# 71. HOVER LANGUAGE

Hover should communicate interactivity without visual noise.

Examples:

```text
link
↓
underline appears

image
↓
slight scale

product
↓
secondary image crossfade

button
↓
small visual state change
```

Avoid giant transformations.

---

# 72. BUTTON SYSTEM

Buttons should be visually restrained.

Primary:

```text
solid
clear
strong contrast
```

Secondary:

```text
outline
or
text action
```

Tertiary:

```text
text link
```

Avoid:

- Excessively rounded pill buttons everywhere.
- Heavy shadows.
- Gradient buttons.
- Pulsing buttons.
- Glow effects.

---

# 73. ICONOGRAPHY

Use icons only where they improve understanding.

Icons should be:

- Consistent.
- Lightweight.
- Clear.
- Accessible.
- Properly aligned.

Do not use unnecessary decorative icon circles everywhere.

---

# 74. FOOTER — MINIMAL SIGNATURE

Footer should be visually light.

Possible desktop structure:

```text
LOGO

Shop           Help           Company
Category       Shipping       About
Category       Returns        Contact

Instagram      Facebook

© Company
```

Only render real data.

Do not create placeholder policy links.

---

# 75. MOBILE EXPERIENCE — PRIMARY PRINCIPLE

MINIMAL mobile should not feel like a compressed desktop site.

Design mobile intentionally.

Mobile priorities:

```text
IMAGE
PRODUCT
NAVIGATION
PURCHASE
TOUCH
SPEED
```

Use:

- Full-width photography.
- Swipe.
- Horizontal product rails.
- Compact navigation.
- Bottom sheets.
- Sticky purchase CTA where appropriate.
- Large touch targets.
- Clear typography.

---

# 76. MOBILE PRODUCT GRID

Possible:

```text
2 columns
```

for standard products.

Or:

```text
1 large column
```

for image-heavy premium products.

Choose based on product image quality and existing tenant catalog.

Do not blindly force two columns.

---

# 77. RESPONSIVE BREAKPOINTS

Test at minimum:

```text
320px
375px
390px
414px
768px
1024px
1280px
1440px
1920px
```

Do not merely test framework default breakpoints.

Check actual visual behavior.

---

# 78. ULTRA-WIDE DISPLAYS

At 1920px+:

do not stretch content to fill the entire monitor.

Use controlled max widths.

Large imagery may extend where composition benefits.

Product text and controls should remain appropriately constrained.

---

# 79. TOUCH INTERACTION

Do not rely on:

```text
hover
```

for critical functionality.

All important actions must work with:

- Touch.
- Keyboard.
- Mouse.

Touch targets:

```text
minimum 44 × 44px
```

where applicable.

---

# 80. ACCESSIBILITY

Target:

# WCAG 2.2 AA

Implement:

- Semantic HTML.
- Keyboard navigation.
- Visible focus.
- Correct labels.
- Accessible forms.
- Sufficient contrast.
- Alt text.
- Accessible menus.
- Accessible dialogs.
- Accessible carousels.
- Error association.
- Reduced-motion support.
- Correct heading hierarchy.

Minimal visual design must never result in invisible focus or insufficient contrast.

---

# 81. FOCUS STATES

Because MINIMAL uses restrained UI, focus states must remain obvious.

Do not remove:

```css
outline
```

without replacing it with an accessible alternative.

Keyboard users must always know where they are.

---

# 82. REDUCED MOTION

Support:

```css
@media (prefers-reduced-motion: reduce)
```

When enabled:

- Disable parallax.
- Remove unnecessary transforms.
- Reduce transition duration.
- Stop unnecessary autoplay.
- Simplify crossfades.

All functionality must remain intact.

---

# 83. PERFORMANCE

MINIMAL must feel fast.

Optimize:

- Initial HTML.
- CSS.
- JavaScript.
- Product images.
- Hero images.
- Font loading.
- Lazy loading.
- Responsive images.
- Layout stability.
- Animations.

Prefer:

```text
transform
opacity
```

for animation.

Avoid heavy animation libraries unless the existing project already uses them and there is strong justification.

---

# 84. IMAGE PERFORMANCE

Use appropriately:

```html
loading="lazy"
```

for non-critical images.

Use:

```css
aspect-ratio
object-fit
```

to prevent layout shift.

Prioritize above-the-fold imagery correctly.

Do not lazy-load critical hero imagery blindly if it damages perceived loading performance.

---

# 85. FONT PERFORMANCE

Do not import unnecessary font families and weights.

If tenant fonts are configurable:

use only approved/supported fonts.

Provide sensible fallbacks.

Avoid blocking rendering with large font payloads.

---

# 86. DATA-DRIVEN CONTENT

All sections must use actual tenant/store data.

Possible data:

- Products.
- Categories.
- Subcategories.
- Brands.
- Collections if supported.
- Promotions.
- Banners.
- Featured products.
- New products.
- Discounted products.
- Benefits.
- Store information.
- AI assistant if supported.

If data does not exist:

hide the corresponding section.

---

# 87. NO FAKE CONTENT

Never invent:

```text
Lorem ipsum
50% OFF
Summer Collection
Best Sellers
#1 Product
Trusted by 10,000 customers
Free shipping
5-star reviews
Limited Edition
Only 3 left
```

unless real data provides that information.

Never fabricate social proof.

---

# 88. NO FAKE FEATURES

Do not invent:

- Wishlist.
- Favorites.
- Reviews.
- Ratings.
- Product comparison.
- Loyalty.
- Coupons.
- Live chat.
- AI recommendations.
- Order tracking.
- Social login.

unless they already exist.

If AI assistant already exists in Qynex:

integrate it visually according to MINIMAL.

Do not create a duplicate AI assistant.

---

# 89. AI ASSISTANT — IF EXISTING

If the existing storefront exposes the Qynex AI shopping assistant:

MINIMAL presentation should be subtle.

Avoid a giant floating colorful chatbot.

Possible:

```text
Need help choosing? Ask Qynex →
```

or a restrained floating control.

Use the existing assistant logic.

---

# 90. TENANT CUSTOMIZATION

Respect existing tenant-controlled properties such as:

- Logo.
- Favicon.
- Primary color.
- Accent color.
- Typography.
- Hero images.
- Promotional images.
- Store name.
- Welcome content.
- Visibility settings.
- Theme configuration.

Tenant customization must not destroy MINIMAL's design integrity.

Implement controlled mapping.

Do not directly inject uncontrolled tenant CSS.

---

# 91. SECURITY

Never permit tenant-provided:

```text
arbitrary JavaScript
arbitrary CSS
arbitrary HTML
scripts
iframes
inline event handlers
```

Dynamic configuration is data.

Not executable code.

Preserve existing security controls.

---

# 92. MULTI-TENANT ISOLATION

Verify:

```text
Tenant A != Tenant B
```

Changing templates must never alter tenant data isolation.

A MINIMAL storefront must retrieve data exactly as the existing storefront architecture requires.

Never bypass tenant resolution.

---

# 93. TEMPLATE RESOLUTION

Use the existing:

```text
StoreTemplate
```

system.

When:

```text
StoreTemplate = MINIMAL
```

render MINIMAL presentation.

Do not modify how business data is resolved solely to support this template.

Fallback behavior remains governed by the existing application architecture.

---

# 94. SHARED COMPONENTS

Reuse shared functionality whenever practical.

However:

do not reuse exact visual markup when doing so prevents MINIMAL from becoming genuinely distinct.

Separate:

```text
BUSINESS BEHAVIOR
```

from:

```text
VISUAL COMPOSITION
```

Shared logic is good.

Forced identical layouts are not.

---

# 95. CSS ARCHITECTURE

Prefer scoped MINIMAL styles.

Possible architecture:

```text
templates/
    minimal/
        minimal.css
        minimal-components.css
        minimal-responsive.css
        minimal-motion.css
```

or the equivalent architecture consistent with the existing project.

Do not blindly create these files if the existing structure uses another pattern.

Follow the project's architecture.

---

# 96. JAVASCRIPT ARCHITECTURE

Do not duplicate existing store logic.

Any MINIMAL-specific JavaScript should primarily manage:

- Visual state.
- Header presentation.
- Drawers.
- Carousels.
- Image transitions.
- UI interactions.
- Motion.
- Responsive behaviors.

Product/cart/order logic must remain in existing application services/modules.

---

# 97. DEPENDENCY RULE

Do not add heavy UI frameworks solely to implement MINIMAL.

Avoid introducing:

- Another CSS framework.
- Large animation packages.
- Large carousel libraries.
- React/Vue/etc.

unless already part of the existing project or clearly justified.

Prefer existing technologies.

---

# 98. PROGRESSIVE ENHANCEMENT

The store should remain usable if advanced animation fails.

Core actions must continue:

```text
search
navigate
view product
select variants
add to cart
checkout
login
register
view orders
```

Motion is enhancement.

Commerce is essential.

---

# 99. SCROLL BEHAVIOR

Never implement scroll hijacking.

Never block native scrolling.

Never force full-screen snap sections across the entire store.

Use native browser behavior.

Smooth scrolling may be used carefully where appropriate.

---

# 100. LAYOUT SHIFT PREVENTION

Prevent CLS.

Reserve space for:

- Product images.
- Hero images.
- Banners.
- Carousels.
- Dynamic controls.

Do not allow content to jump significantly after load.

---

# 101. MODALS AND DRAWERS

If used:

support:

- Escape key.
- Proper focus.
- Focus return.
- Background scroll locking where appropriate.
- Accessible labels.
- Clear close button.
- Touch.
- Responsive behavior.

Use them sparingly.

---

# 102. ANTI-GENERIC RULES

The following output is unacceptable:

```text
white navbar
big hero
four cards
three product sections
rounded buttons
generic footer
```

The following is also unacceptable:

```text
same CLASSIC HTML
+
minimal.css
```

MINIMAL must have its own visual composition.

---

# 103. ANTI-OVERDESIGN RULES

Also unacceptable:

```text
animated cursor
floating particles
3D cards
neon
glass everywhere
scroll hijacking
massive gradients
oversized decorative circles
constant transitions
animations on every text block
```

Premium minimalism comes from discipline.

---

# 104. HOMEPAGE QUALITY TEST

Ask:

```text
If I remove the logo,
does this still clearly feel like MINIMAL?
```

If the answer is no:

the visual identity is too generic.

---

# 105. PRODUCT PAGE QUALITY TEST

Ask:

```text
Does the product feel more important than the UI?
```

If not:

simplify.

---

# 106. MOBILE QUALITY TEST

Ask:

```text
Does this feel intentionally designed for a phone?
```

not:

```text
Does the desktop layout technically fit on a phone?
```

If it feels compressed:

redesign.

---

# 107. CONVERSION TEST

Minimalism must never hide critical commerce information.

Always make clear:

```text
PRODUCT
PRICE
STOCK
VARIANT
QUANTITY
ADD TO CART
CHECKOUT
TOTAL
PAYMENT
```

Visual restraint must not reduce conversion.

---

# 108. REQUIRED PAGES

MINIMAL must fully cover:

```text
tienda/index.html
tienda/producto.html
tienda/carrito.html
tienda/checkout.html
tienda/cuenta/login.html
tienda/cuenta/registro.html
tienda/cuenta/pedidos.html
tienda/no-disponible.html
```

Do not redesign only the homepage.

The complete customer journey must feel like one coherent MINIMAL product.

---

# 109. IMPLEMENTATION PHASE 1 — AUDIT

Before changing code:

document internally:

- Relevant files.
- Existing data flow.
- Existing APIs.
- Shared store components.
- Template resolution.
- Cart behavior.
- Checkout behavior.
- Authentication behavior.
- Responsive architecture.
- Existing CSS conflicts.

Do not begin large refactors before understanding the system.

---

# 110. IMPLEMENTATION PHASE 2 — DESIGN ARCHITECTURE

Define:

- MINIMAL design tokens.
- Typography.
- Container widths.
- Spacing.
- Grid.
- Header system.
- Navigation.
- Product card.
- Catalog.
- Product detail.
- Cart.
- Checkout.
- Account.
- Motion.

Make these decisions coherently before random page-by-page styling.

---

# 111. IMPLEMENTATION PHASE 3 — SHELL

Implement the global MINIMAL shell:

- Background.
- Typography.
- Header.
- Navigation.
- Search.
- Mobile navigation.
- Footer.
- Containers.
- Global states.

Verify responsive behavior.

---

# 112. IMPLEMENTATION PHASE 4 — HOMEPAGE

Implement:

- Real hero behavior.
- No-banner behavior.
- Visual categories.
- Product sections.
- Featured products.
- Relevant promotions.
- Brand content.
- Carousels where useful.
- Responsive behavior.

Avoid unsupported sections.

---

# 113. IMPLEMENTATION PHASE 5 — CATALOG

Implement:

- Search presentation.
- Filters.
- Sorting.
- Product grid.
- Empty results.
- Loading.
- Errors.
- Mobile filters.
- Responsive product cards.

Preserve filtering logic.

---

# 114. IMPLEMENTATION PHASE 6 — PRODUCT

Give extremely high priority to:

- Large gallery.
- Image transitions.
- Product information.
- Price hierarchy.
- Variant selection.
- Quantity.
- Stock.
- CTA.
- Sticky purchase when appropriate.
- Mobile gallery.
- Secondary information.
- Loading and errors.

---

# 115. IMPLEMENTATION PHASE 7 — CART

Verify:

- Product rendering.
- Quantity.
- Removal.
- Totals.
- Empty cart.
- Loading.
- Errors.
- Mobile behavior.

Do not alter calculations.

---

# 116. IMPLEMENTATION PHASE 8 — CHECKOUT

Verify:

- Customer information.
- Address.
- Shipping.
- Payment.
- Order summary.
- Validation.
- Errors.
- Loading.
- Confirmation.
- Mobile keyboard behavior.
- Conversion clarity.

Do not alter checkout business rules.

---

# 117. IMPLEMENTATION PHASE 9 — ACCOUNT

Implement visual consistency for:

```text
login
registration
orders
```

No generic admin dashboard styling.

Keep the experience consumer-facing.

---

# 118. IMPLEMENTATION PHASE 10 — MOTION

Only after core UX works:

add:

- Crossfades.
- Header transition.
- Product image transitions.
- Scroll reveals.
- Drawer motion.
- Modal motion.
- Cart feedback.
- Carousel transitions.

Do not build motion before functionality is stable.

---

# 119. QA — DESKTOP

Test:

```text
1024
1280
1440
1920
```

Verify:

- Content widths.
- Image quality.
- Spacing.
- Header.
- Navigation.
- Product grid.
- Product detail.
- Cart.
- Checkout.
- Forms.
- Footer.

---

# 120. QA — MOBILE

Test:

```text
320
375
390
414
```

Verify:

- No horizontal page overflow.
- Header usability.
- Search.
- Menu.
- Category discovery.
- Product cards.
- Swipe.
- Filters.
- Product gallery.
- Variants.
- Sticky CTA.
- Cart.
- Checkout.
- Forms.
- Keyboard opening.
- Bottom sheets.
- Safe areas.

---

# 121. QA — TABLET

Test:

```text
768
1024
```

Do not simply inherit desktop or mobile behavior blindly.

Tablet needs intentional layouts.

---

# 122. ACCESSIBILITY QA

Verify:

- Tab navigation.
- Shift+Tab.
- Escape.
- Enter.
- Space where appropriate.
- Visible focus.
- Labels.
- Form error association.
- Dialog focus management.
- Carousel accessibility.
- Contrast.
- Heading order.
- Reduced motion.

---

# 123. FUNCTIONAL QA — PRODUCT

Verify:

```text
product load
price
discount
stock
variants
quantity
add to cart
buy now if existing
```

Do not accept visual success with broken commerce.

---

# 124. FUNCTIONAL QA — CART

Verify:

```text
add
remove
increase
decrease
persistence
subtotal
discount
shipping
total
```

Use real existing calculations.

---

# 125. FUNCTIONAL QA — CHECKOUT

Verify:

```text
customer
address
shipping
payment
validation
confirmation
errors
```

Do not modify tests to hide problems.

---

# 126. FUNCTIONAL QA — ACCOUNT

Verify:

```text
login
logout if existing
registration
session
orders
order details if existing
```

---

# 127. TEMPLATE SWITCH QA

Test:

```text
CLASSIC
↓
MINIMAL
↓
CLASSIC
```

The same tenant must retain:

- Products.
- Prices.
- Cart.
- Orders.
- Customer information.
- Configuration.
- Payment configuration.
- Store data.

Only presentation should change.

---

# 128. PERFORMANCE QA

Check for:

- Layout shifts.
- Oversized images.
- Duplicate JavaScript.
- Excessive CSS.
- Expensive scroll handlers.
- Unnecessary event listeners.
- Forced reflows.
- Unoptimized animations.
- Blocking fonts.
- Large dependencies.

The visual experience must remain smooth on ordinary mobile hardware.

---

# 129. CODE QUALITY

Maintain:

- Clear naming.
- Reusable components.
- Small functions.
- Separation of concerns.
- No dead code.
- No unnecessary dependencies.
- No duplicated business logic.
- No inline chaos.
- No massive monolithic event handlers.

Respect existing architecture.

---

# 130. VALIDATION

Use all project validation tools that already exist.

Where applicable run:

```bash
node --check
```

Also run available:

```text
build
lint
typecheck
tests
```

Fix errors caused by the implementation.

Do not hide failing tests.

---

# 131. FINAL VISUAL ACCEPTANCE CRITERIA

MINIMAL passes only if:

### Identity

- It does not look like CLASSIC.
- It does not look like a Bootstrap template.
- It does not look unfinished.
- It has a clear product-first identity.
- Whitespace feels intentional.
- Product imagery dominates appropriately.

### UX

- Navigation is immediately understandable.
- Search remains accessible.
- Products are easy to discover.
- Price is immediately visible.
- Purchasing is obvious.
- Forms are simple.
- Checkout is distraction-free.

### Motion

- Motion is subtle.
- Crossfades feel smooth.
- No excessive animation.
- Reduced motion works.
- Animations do not block content.

### Mobile

- Mobile feels intentionally designed.
- Swipe works where appropriate.
- Filters are usable.
- Purchase actions remain accessible.
- No accidental overflow.
- Touch targets are appropriate.

### Technical

- Existing APIs preserved.
- Existing product logic preserved.
- Existing cart preserved.
- Existing checkout preserved.
- Existing payments preserved.
- Existing authentication preserved.
- Existing orders preserved.
- Tenant isolation preserved.
- No fake features.
- No fake content.
- No secrets exposed.
- Build remains functional.

---

# 132. FINAL DIRECTIVE

Do not interpret MINIMAL as:

> "Make the existing store simpler."

Interpret it as:

> **Design a premium product-first digital commerce experience where the interface becomes almost invisible and the merchandise becomes the protagonist.**

MINIMAL must feel:

```text
SPACIOUS
CALM
PRECISE
MODERN
FAST
PRODUCT-FIRST
REFINED
INTENTIONAL
RESPONSIVE
ACCESSIBLE
COMMERCIAL
```

The storefront should never feel empty.

It should feel:

**CURATED.**

It should never feel unfinished.

It should feel:

**DELIBERATE.**

It should never feel like CLASSIC with fewer elements.

It should feel like:

# AN ENTIRELY DIFFERENT PREMIUM ECOMMERCE EXPERIENCE.

Every visual decision must reinforce:

```text
PRODUCT > INTERFACE
CLARITY > DECORATION
SPACE > NOISE
QUALITY > EFFECTS
FUNCTION > ORNAMENT
```

Finally:

**Business logic remains authoritative.**

**MINIMAL controls presentation.**

**The existing Qynex ecommerce engine remains the single source of truth.**