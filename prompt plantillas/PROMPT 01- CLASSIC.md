# QYNEX ECOMMERCE PLUS
# TEMPLATE PROMPT — CLASSIC
## Premium Professional Retail Ecommerce Experience

---

# 01. ROLE

Act as a multidisciplinary senior product team composed of:

- Senior Staff Frontend Engineer.
- Senior UI/UX Designer.
- Senior Ecommerce UX Specialist.
- Design Systems Architect.
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

# CLASSIC TEMPLATE

Template identifier:

```text
CLASSIC
```

This is not a generic ecommerce template.

It must feel like a **premium, commercially viable ecommerce storefront that a real business would confidently pay to use**.

The objective is not simply to make the current storefront prettier.

The objective is to transform the existing ecommerce engine into a polished, trustworthy, high-converting commercial storefront while preserving every existing business capability.

---

# 02. CORE DESIGN MISSION

CLASSIC is the flagship general-purpose Qynex storefront.

It must communicate:

```text
TRUST
PROFESSIONALISM
CLARITY
QUALITY
CONVERSION
STABILITY
```

The experience should immediately communicate:

> "This is a serious online store."

It must work exceptionally well for:

- Retail.
- Electronics.
- Technology.
- Hardware.
- Home products.
- General stores.
- Consumer products.
- B2C businesses.
- Small and medium businesses.
- Professional commercial brands.

CLASSIC must be versatile enough to work with many types of products without becoming visually generic.

---

# 03. ABSOLUTE RULE — THIS IS NOT A COLOR THEME

Do NOT build CLASSIC as:

```text
generic ecommerce
+
brand color
+
cards
+
hero
```

That is unacceptable.

CLASSIC requires its own:

- Layout system.
- Header composition.
- Navigation behavior.
- Product card design.
- Hero behavior.
- Section hierarchy.
- Spacing rhythm.
- Typography hierarchy.
- Motion language.
- Interaction patterns.
- Mobile architecture.
- Product detail experience.
- Cart presentation.
- Checkout presentation.

The storefront should feel intentionally designed from the beginning.

---

# 04. BUSINESS LOGIC IS AUTHORITATIVE

This template controls:

```text
PRESENTATION
VISUAL DESIGN
INTERACTION DESIGN
MOTION
LAYOUT
RESPONSIVE BEHAVIOR
```

The existing ecommerce engine controls:

```text
PRODUCTS
CATALOG
STOCK
PRICES
DISCOUNTS
PROMOTIONS
CART
CHECKOUT
PAYMENTS
ORDERS
SHIPPING
AUTHENTICATION
CUSTOMERS
TENANTS
NOTIFICATIONS
APIs
BUSINESS RULES
```

Never replace existing business logic.

Never create:

```text
second cart
second checkout
second product API
second authentication system
second order system
```

Reuse the existing implementation.

If a required visual interaction conflicts with the existing architecture:

**DO NOT force the implementation.**

Identify the conflict and use the smallest safe architectural change.

---

# 05. FIRST ACTION — AUDIT THE EXISTING SYSTEM

Before modifying code, inspect the existing storefront completely.

At minimum inspect:

```text
front/tienda/
front/tienda/js/store/
store-shell.js
store-api.js
```

Also inspect everything related to:

```text
products
catalog
categories
brands
cart
checkout
payments
orders
authentication
customer account
tenant configuration
notifications
existing styles
existing components
```

Determine:

- How tenant resolution works.
- How StoreTemplate is obtained.
- How products are loaded.
- How categories are loaded.
- How product detail works.
- How the cart works.
- How checkout works.
- How authentication works.
- How orders work.
- Which DOM selectors are used.
- Which events already exist.
- Which functions already exist.
- Which styles are globally shared.
- Which components can safely be reused.

Do not guess.

Read the implementation first.

---

# 06. CLASSIC VISUAL PHILOSOPHY

CLASSIC should look:

- Premium.
- Professional.
- Modern.
- Clean.
- Commercial.
- Confident.
- Organized.
- Familiar without being boring.

Avoid:

- Generic Bootstrap appearance.
- Excessive rounded cards.
- Excessive shadows.
- Random gradients.
- Giant unnecessary text.
- Excessive glassmorphism.
- Decorative UI with no purpose.
- Overloaded dashboards.
- Template-marketplace aesthetics.
- Artificial "AI-generated" design patterns.

The design must feel intentional.

---

# 07. DESIGN PSYCHOLOGY

The customer should experience the following progression:

```text
DISCOVER
↓
UNDERSTAND
↓
TRUST
↓
EXPLORE
↓
SELECT
↓
ADD TO CART
↓
CHECKOUT
↓
PURCHASE
```

Every visual decision should support this journey.

The interface must never make the customer wonder:

```text
Where are the products?
Where is the price?
Where is the cart?
How do I buy?
What happens next?
```

---

# 08. GLOBAL VISUAL SYSTEM

Create a controlled CLASSIC design system.

Use design tokens for:

```css
--classic-primary
--classic-secondary
--classic-accent

--classic-background
--classic-surface
--classic-surface-alt

--classic-text
--classic-text-muted

--classic-border

--classic-success
--classic-warning
--classic-error

--classic-radius-sm
--classic-radius-md
--classic-radius-lg

--classic-shadow-sm
--classic-shadow-md
--classic-shadow-lg

--classic-container
--classic-section-gap

--classic-transition-fast
--classic-transition-normal
--classic-transition-slow
```

Do not hardcode visual values throughout dozens of components.

---

# 09. TYPOGRAPHY

Typography must communicate professionalism.

Establish clear hierarchy:

```text
Display
H1
H2
H3
Body
Small
Label
Caption
Price
CTA
```

Prices must be visually prominent.

Product names must be readable.

Secondary information must remain subordinate.

Do not use too many font sizes.

Use the tenant's approved typography configuration when supported.

---

# 10. CONTAINER SYSTEM

Use a professional responsive container.

The desktop experience should not become an excessively wide wall of content.

Use:

```text
mobile
tablet
desktop
large desktop
ultra-wide
```

On large displays, maintain visual rhythm and readable content widths.

Do not stretch product grids unnecessarily.

---

# 11. HEADER — CLASSIC SIGNATURE

The header is one of the most important components.

Create a distinctive CLASSIC header.

Desktop composition should generally support:

```text
┌──────────────────────────────────────────────────────────────┐
│ LOGO        SEARCH                         ACCOUNT   CART    │
├──────────────────────────────────────────────────────────────┤
│ CATEGORIES     CATEGORY     CATEGORY     CATEGORY     MORE   │
└──────────────────────────────────────────────────────────────┘
```

Adapt according to available real data.

The header must support existing:

- Logo.
- Store name.
- Search.
- Categories.
- Account.
- Cart.
- Cart count.
- Navigation.
- Mobile menu.

Do not invent unavailable features.

---

# 12. HEADER MOTION

The header should react intelligently to scrolling.

Initial state:

```text
FULL HEADER
```

After scrolling:

```text
COMPACT HEADER
```

Use a smooth transition.

For example:

```text
Logo
↓
slightly reduces
↓
navigation becomes more compact
↓
search remains accessible
↓
cart/account remain visible
```

Do not abruptly hide important functionality.

The transition should use:

```text
transform
opacity
height
```

only when performance remains good.

Avoid layout jumps.

---

# 13. SEARCH EXPERIENCE

Search is a major conversion tool.

Design professional states:

```text
DEFAULT
FOCUS
TYPING
LOADING
RESULTS
NO RESULTS
ERROR
```

If the existing backend supports suggestions, present them using a polished suggestion panel.

Example structure:

```text
┌─────────────────────────────────────────────┐
│ Search products...                          │
├─────────────────────────────────────────────┤
│ PRODUCTS                                    │
│                                             │
│ [IMG] Product name                 S/ XX.XX │
│ [IMG] Product name                 S/ XX.XX │
│                                             │
│ CATEGORIES                                  │
│ Electronics                                 │
│ Accessories                                 │
└─────────────────────────────────────────────┘
```

Do not create a new search engine.

Use the existing search implementation.

---

# 14. NAVIGATION

Category navigation should feel fast and intuitive.

When many categories exist:

- Use horizontal navigation.
- Use appropriate overflow behavior.
- Consider a structured "more" interaction.
- Do not create impossible navigation bars.

On mobile:

```text
← Category
  Category
  Category
  Category →
```

Use horizontal touch scrolling when appropriate.

---

# 15. HERO EXPERIENCE

When real banners exist, create a premium hero carousel.

Support:

- Multiple slides.
- Image.
- Headline.
- Description.
- CTA.
- Promotional information when provided.
- Indicators.
- Previous/next controls.

Visual composition:

```text
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   REAL STORE CONTENT                                        │
│                                                             │
│   HEADLINE                                                  │
│   Description                                               │
│                                                             │
│   [ CTA ]                                                   │
│                                                             │
│                          ● ○ ○                              │
└─────────────────────────────────────────────────────────────┘
```

Do not use fake promotional text.

Do not invent:

```text
50% OFF
Best Deal
Buy Now
New Collection
```

unless supplied by actual store data.

---

# 16. HERO MOTION

Hero motion must feel premium.

Use:

- Smooth crossfade.
- Subtle image movement.
- Text entrance.
- CTA transition.
- Slide indicators.
- Touch swipe.

Avoid:

- Aggressive zoom.
- Excessive parallax.
- Fast flashing.
- Large movement.
- Distracting transitions.

Autoplay may be used if appropriate.

Pause when necessary.

Respect:

```text
prefers-reduced-motion
```

---

# 17. NO-BANNER EXPERIENCE

If no real banners exist:

**Do not create a fake hero.**

Instead, intelligently adapt the homepage.

Possible alternatives:

```text
Featured products
↓
Categories
↓
Product discovery
```

The page must look intentional.

Never leave a giant empty hero area.

---

# 18. HOMEPAGE ARCHITECTURE

The homepage should be assembled dynamically according to real tenant data.

Preferred hierarchy:

```text
HEADER
↓
HERO
↓
CATEGORY DISCOVERY
↓
FEATURED PRODUCTS
↓
PROMOTIONAL SECTION
↓
NEW PRODUCTS
↓
POPULAR PRODUCTS
↓
BRANDS
↓
BENEFITS
↓
FOOTER
```

But:

**only render sections supported by real data.**

If:

```text
featuredProducts = []
```

hide the section.

Do not leave empty containers.

---

# 19. SECTION DESIGN

Every section needs a visual purpose.

Do not make the page a collection of:

```text
heading
cards
heading
cards
heading
cards
```

Use variation.

Examples:

```text
full-width visual
product rail
grid
split layout
horizontal categories
featured product
promotional block
brand strip
```

But only where real data supports it.

---

# 20. PRODUCT DISCOVERY

CLASSIC must make product discovery effortless.

Use:

- Product grids.
- Product carousels.
- Category rails.
- Featured products.
- New products.
- Discounted products.
- Best sellers when supported.
- Brands when supported.

Do not overwhelm the customer.

Prioritize hierarchy.

---

# 21. PRODUCT CARD — CLASSIC SIGNATURE

Create a polished reusable product card.

Structure:

```text
┌──────────────────────────────┐
│                              │
│          PRODUCT IMAGE       │
│                              │
│                  [BADGE]     │
│                              │
├──────────────────────────────┤
│ Brand                        │
│ Product name                 │
│                              │
│ S/ XXX.XX                    │
│ S/ XXX.XX                    │
│                              │
│ [ ADD TO CART ]              │
└──────────────────────────────┘
```

Only show information available from real data.

---

# 22. PRODUCT CARD MOTION

Desktop hover may trigger:

```text
IMAGE
↓
subtle zoom

secondary image
↓
crossfade

CTA
↓
smooth appearance
```

Use subtle elevation.

Do not turn the card into an animated advertisement.

Motion should feel:

```text
smooth
fast
predictable
premium
```

---

# 23. PRODUCT IMAGE BEHAVIOR

If a secondary product image exists:

On hover:

```text
primary image
→
secondary image
```

Use crossfade rather than abrupt replacement.

If there is no secondary image:

Do not simulate one.

Maintain image aspect ratio.

Prevent layout shifts.

---

# 24. ADD TO CART INTERACTION

When the user adds a product:

```text
ADD TO CART
↓
visual confirmation
↓
cart count updates through existing logic
```

Example:

```text
[ ADD TO CART ]

        ↓

[ ✓ ADDED ]
```

The confirmation must be immediate.

If technically compatible with the existing architecture, use a subtle visual movement toward the cart.

Do not create a second cart system.

---

# 25. PRODUCT CAROUSELS

Use horizontal product rails where they improve discovery.

Desktop:

```text
← Product | Product | Product | Product →
```

Mobile:

```text
← Product | Product →
```

Support:

- Mouse interaction.
- Touch swipe.
- Keyboard navigation.
- Accessible controls.
- Responsive card sizing.

Do not allow accidental page-wide horizontal scrolling.

---

# 26. CATALOG PAGE

CLASSIC catalog should balance:

```text
DISCOVERY
+
INFORMATION
+
CONVERSION
```

Desktop:

```text
┌────────────┬─────────────────────────────────────┐
│ FILTERS    │ SORT                               │
│            ├─────────────────────────────────────┤
│ Category   │ Product Product Product Product    │
│ Brand      │ Product Product Product Product    │
│ Price      │ Product Product Product Product    │
│ Stock      │                                     │
└────────────┴─────────────────────────────────────┘
```

Use existing filtering and sorting logic.

---

# 27. MOBILE CATALOG

On mobile use:

```text
┌───────────────────────────────┐
│ Search                        │
├───────────────┬───────────────┤
│ FILTERS       │ SORT          │
└───────────────┴───────────────┘
```

Filters should open as:

- Drawer.
- Bottom sheet.
- Modal.

Choose the interaction that best matches CLASSIC.

Support:

- Escape.
- Focus management.
- Touch.
- Scroll.
- Close.
- Apply.
- Clear.

---

# 28. PRODUCT DETAIL — HIGH PRIORITY

Product detail must feel substantially more premium than the catalog.

Desktop:

```text
┌────────────────────┬──────────────────────────────┐
│                    │ PRODUCT NAME                 │
│                    │ Brand                        │
│     IMAGE          │ Rating only if existing      │
│     GALLERY        │                              │
│                    │ PRICE                        │
│                    │ STOCK                        │
│                    │ VARIANTS                     │
│                    │ QUANTITY                     │
│                    │                              │
│                    │ [ ADD TO CART ]              │
│                    │ [ BUY NOW ]                  │
│                    │                              │
│                    │ Shipping information          │
│                    │ Payment information           │
└────────────────────┴──────────────────────────────┘
```

---

# 29. PRODUCT GALLERY

Support existing capabilities for:

- Thumbnails.
- Main image.
- Swipe.
- Zoom.
- Fullscreen/lightbox.
- Keyboard navigation.

Do not implement unsupported functionality merely for visual purposes.

Mobile should prioritize the product image:

```text
← IMAGE →
1 / 5
```

The gallery must feel smooth.

---

# 30. PRODUCT INFORMATION HIERARCHY

The customer should immediately understand:

```text
WHAT IS IT?
↓
HOW MUCH?
↓
IS IT AVAILABLE?
↓
WHAT OPTIONS DO I HAVE?
↓
HOW MANY?
↓
HOW DO I BUY?
```

Do not bury the purchase action below unnecessary content.

---

# 31. STICKY PURCHASE EXPERIENCE

On long product pages, consider a sticky purchase bar.

Desktop:

```text
PRODUCT | PRICE | [ ADD TO CART ]
```

Mobile:

```text
┌────────────────────────────────┐
│ S/ XXX.XX          [ BUY NOW ] │
└────────────────────────────────┘
```

Only use it where it improves usability.

Never cover important content.

Respect safe areas.

Do not duplicate business logic.

---

# 32. CART PAGE

The cart should feel clean and reassuring.

Desktop:

```text
PRODUCTS
────────────────────────────────────
Product
Variant
Price
Quantity
Subtotal
Remove

                              SUMMARY
                              Subtotal
                              Shipping
                              Discount
                              Total

                              [ CHECKOUT ]
```

Use the existing cart state.

Do not create duplicate calculations.

---

# 33. CART MICROINTERACTIONS

When quantity changes:

```text
quantity
↓
small transition
↓
subtotal updates
```

When an item is removed:

```text
item
↓
smooth exit
↓
summary updates
```

Avoid animations that delay important information.

---

# 34. EMPTY CART

Never show only:

```text
Your cart is empty.
```

Create a polished empty state.

Structure:

```text
[ VISUAL ]

Your cart is waiting for something great.

Explore our products and find something you'll love.

[ CONTINUE SHOPPING ]
```

The text must be appropriate to the tenant and must not invent unsupported claims.

---

# 35. CHECKOUT

Checkout must intentionally become quieter.

Remove unnecessary visual distractions.

Prioritize:

```text
CUSTOMER
↓
DELIVERY
↓
PAYMENT
↓
ORDER SUMMARY
↓
CONFIRMATION
```

The customer must always understand:

```text
What am I buying?
How much am I paying?
How will I pay?
Where will it be delivered?
How do I confirm?
```

Do not create a new checkout.

---

# 36. LOGIN

Create a polished CLASSIC authentication experience.

Include only existing fields.

Design:

- Input hierarchy.
- Clear labels.
- Focus states.
- Password visibility when supported.
- Loading.
- Errors.
- Success.
- Recovery when supported.

Do not add unsupported fields.

---

# 37. REGISTRATION

Make registration feel simple.

Avoid visual intimidation.

Use:

```text
STEP
↓
INPUT
↓
VALIDATION
↓
CONTINUE
```

when compatible with the existing form.

Never alter backend requirements.

---

# 38. CUSTOMER ACCOUNT

The customer account should use CLASSIC's professional visual language.

Orders should be presented clearly:

```text
ORDER #12345
DATE
TOTAL
STATUS
[ VIEW DETAILS ]
```

Support:

```text
loading
empty
error
success
```

Use existing order data.

---

# 39. STORE UNAVAILABLE

Create a polished technical-safe page.

Never display:

```text
500 Internal Server Error
NullPointerException
TenantResolutionException
```

Instead communicate:

```text
This store is temporarily unavailable.

Please try again later.
```

Only use information appropriate to the actual state.

---

# 40. MOTION SYSTEM

CLASSIC uses:

# "CONFIDENT MOTION"

Motion must be:

```text
SMOOTH
CONTROLLED
FAST ENOUGH
NOT DISTRACTING
```

Use motion for:

- Header transitions.
- Hero transitions.
- Product hover.
- Product image changes.
- Carousels.
- Scroll reveal.
- Drawer opening.
- Modal transitions.
- Cart feedback.
- State changes.
- Gallery transitions.

---

# 41. MOTION TIMING

Establish consistent timing.

Suggested direction:

```text
Micro interaction:
120–180ms

Normal transition:
200–300ms

Large transition:
350–500ms
```

Do not blindly apply these values if the existing architecture requires otherwise.

Use easing that feels natural.

Prefer:

```text
transform
opacity
```

for high-performance animations.

---

# 42. SCROLL REVEAL

Use scroll reveal selectively.

Good candidates:

- Section entrances.
- Promotional blocks.
- Featured products.
- Category sections.

Avoid:

```text
every card animating independently
```

The user should never wait for content to become usable.

---

# 43. PARALLAX

Parallax is optional.

Use only for:

- Hero.
- Large visual banner.
- Premium promotional section.

Keep it subtle.

Never make the entire page move.

Disable or significantly reduce it under:

```css
@media (prefers-reduced-motion: reduce)
```

---

# 44. MOBILE EXPERIENCE

Mobile is a first-class experience.

Do not simply shrink desktop.

Prioritize:

```text
ONE-HAND USABILITY
FAST DISCOVERY
TOUCH
READABILITY
PURCHASE
```

Use:

- Compact header.
- Search access.
- Horizontal category scrolling.
- Swipeable carousels.
- Filter drawers.
- Bottom sheets.
- Sticky CTA where appropriate.
- Optimized gallery.
- Simplified checkout.

Minimum touch target:

```text
44px × 44px
```

---

# 45. RESPONSIVE BREAKPOINTS

Explicitly validate:

```text
320px
360px
375px
390px
414px
480px
768px
1024px
1280px
1440px
1920px+
```

Do not rely on one breakpoint.

Check:

- Header.
- Search.
- Navigation.
- Hero.
- Cards.
- Grids.
- Gallery.
- Cart.
- Checkout.
- Modals.
- Drawers.
- Sticky elements.

---

# 46. LOADING EXPERIENCE

Do not use giant generic loaders.

Use localized skeletons.

For example:

```text
Product card skeleton
Hero skeleton
Product detail skeleton
Catalog skeleton
Order skeleton
```

Skeleton structure must closely resemble the actual component.

---

# 47. EMPTY STATES

Every major data-driven section must have a graceful empty behavior.

Prefer:

```text
NO DATA
↓
remove unnecessary section
```

instead of:

```text
NO DATA
↓
giant blank area
```

For functional pages such as cart/orders, use meaningful empty states.

---

# 48. ERROR STATES

Errors must be:

```text
CLEAR
CALM
ACTIONABLE
NON-TECHNICAL
```

Never expose raw backend errors to customers.

---

# 49. SUCCESS STATES

Success feedback must be immediate.

Examples:

```text
Product added
Order completed
Profile updated
Quantity changed
```

Use subtle motion.

Do not block the customer unnecessarily.

---

# 50. ACCESSIBILITY

Target:

# WCAG 2.2 AA

Ensure:

- Semantic HTML.
- Keyboard navigation.
- Visible focus.
- Accessible forms.
- Accessible buttons.
- Accessible carousels.
- Accessible dialogs.
- Accessible drawers.
- Proper labels.
- Alt text.
- Contrast.
- Reduced motion.
- Screen-reader compatibility.
- Touch targets ≥44px.

Do not sacrifice accessibility for visual effects.

---

# 51. TENANT CUSTOMIZATION

Respect all existing tenant customization.

Potentially configurable:

```text
logo
favicon
primary color
secondary color
accent
approved fonts
background
light/dark mode
radius
hero
banners
welcome text
category visibility
brand visibility
promotion visibility
AI assistant visibility
```

Do not remove these capabilities.

Do not hardcode CLASSIC's colors over tenant configuration.

Use controlled design variables.

---

# 52. SECURITY

Never allow tenant-controlled:

```text
arbitrary CSS
arbitrary JavaScript
arbitrary HTML
arbitrary iframe
arbitrary scripts
inline event handlers
```

Dynamic content is data.

Never execute tenant-provided code.

Never expose:

```text
API secrets
tokens
credentials
private identifiers
```

---

# 53. DATA-DRIVEN RENDERING

Everything must use real application data.

Never hardcode:

```text
products
prices
categories
brands
stock
discounts
promotions
payment methods
shipping
orders
company information
```

Do not use fake production content.

---

# 54. NO FAKE FEATURES

Do not add visual-only fake implementations of:

```text
wishlist
reviews
comparison
loyalty
coupons
tracking
chat
AI recommendations
favorites
```

unless they already exist.

If the feature exists:

**make it excellent.**

If it does not exist:

**do not fake it.**

---

# 55. AI ASSISTANT

If the existing storefront already provides an AI assistant:

Integrate it visually into CLASSIC.

The widget must:

- Match the template.
- Avoid covering important CTAs.
- Work on mobile.
- Respect accessibility.
- Respect tenant visibility configuration.

Do not create a new AI backend.

---

# 56. FOOTER

CLASSIC footer should feel professional and complete.

When data exists, organize:

```text
COMPANY
CATEGORIES
CONTACT
SOCIAL
POLICIES
TERMS
PAYMENT
SHIPPING
```

Do not show unavailable information.

Do not invent:

```text
phone numbers
addresses
social networks
certifications
payment methods
```

---

# 57. PERFORMANCE

The storefront must feel fast.

Optimize:

- Images.
- Lazy loading.
- Responsive images.
- CSS.
- JavaScript.
- Fonts.
- Layout stability.
- Motion.
- Initial rendering.

Avoid heavy dependencies.

Do not add animation libraries unless genuinely necessary.

Prefer native CSS and existing project capabilities.

---

# 58. IMAGE RULES

Always preserve product image quality.

Use:

```text
aspect-ratio
object-fit
lazy loading
responsive images
```

Prevent layout shifts.

Never stretch images.

Never create fake images.

---

# 59. REDUCED MOTION

Implement:

```css
@media (prefers-reduced-motion: reduce)
```

Reduce:

- Transition duration.
- Autoplay.
- Parallax.
- Scroll animation.
- Decorative movement.

Functionality must remain intact.

---

# 60. ANTI-GENERIC DESIGN RULE

Before accepting any visual decision, ask:

> "Could this exact interface belong to any random ecommerce template?"

If YES:

**redesign it.**

CLASSIC must have a recognizable visual identity.

It should look like a deliberate Qynex commercial product.

Not:

```text
Bootstrap
Tailwind demo
Shopify clone
AI generated template
generic marketplace UI
```

---

# 61. DO NOT OVERDESIGN

Premium does not mean:

```text
more shadows
more gradients
more animations
more rounded corners
more glass
more effects
```

Premium means:

```text
better hierarchy
better spacing
better typography
better interaction
better transitions
better composition
better usability
```

---

# 62. QUALITY BAR

The implementation should be judged against premium commercial ecommerce products.

The target feeling is:

```text
POLISHED
TRUSTWORTHY
FAST
MODERN
EXPENSIVE
PROFESSIONAL
```

The user should feel that this storefront could legitimately be used by:

```text
a serious retail company
```

without redesigning it first.

---

# 63. COMPONENT ARCHITECTURE

Where compatible with the existing project, create/reuse:

```text
ClassicHeader
ClassicMobileHeader
ClassicNavigation
ClassicSearch
ClassicHero
ClassicSection
ClassicCategoryRail
ClassicProductCard
ClassicProductGrid
ClassicProductCarousel
ClassicProductGallery
ClassicProductInfo
ClassicVariantSelector
ClassicQuantitySelector
ClassicPrice
ClassicDiscountBadge
ClassicStockBadge
ClassicCartItem
ClassicCartSummary
ClassicCheckoutSummary
ClassicLoginForm
ClassicRegisterForm
ClassicOrderCard
ClassicEmptyState
ClassicErrorState
ClassicLoadingState
ClassicFooter
ClassicAIWidget
```

Adapt naming to the project's actual architecture.

Do not duplicate components unnecessarily.

---

# 64. TEMPLATE ISOLATION

The CLASSIC visual system must not accidentally modify:

```text
MINIMAL
FASHION
SPORT
LUXURY
BOUTIQUE
CATALOG
MARKET
EDITORIAL
URBAN
```

Template-specific styles must be isolated.

Avoid global CSS pollution.

Do not create selectors that unintentionally affect other templates.

---

# 65. TEMPLATE SWITCHING

When:

```text
StoreTemplate = CLASSIC
```

the CLASSIC experience must load.

When switching to another official template, the underlying:

```text
products
cart
checkout
orders
customer
payments
```

must remain unchanged.

---

# 66. FUNCTIONAL QA

Test:

### Catalog

- Products.
- Categories.
- Search.
- Filters.
- Sorting.
- Brands.
- Pagination/load-more if existing.

### Product

- Images.
- Gallery.
- Variants.
- Quantity.
- Stock.
- Price.
- Add to cart.
- Buy now if existing.

### Cart

- Add.
- Remove.
- Quantity.
- Persistence.
- Totals.

### Checkout

- Customer.
- Shipping.
- Payment.
- Confirmation.
- Errors.
- Loading.

### Account

- Login.
- Registration.
- Session.
- Orders.

---

# 67. RESPONSIVE QA

Test:

```text
320
360
375
390
414
480
768
1024
1280
1440
1920
```

Verify:

```text
header
navigation
hero
products
catalog
filters
gallery
cart
checkout
account
drawers
modals
sticky elements
```

No horizontal overflow.

No clipped content.

No broken CTAs.

No unreadable typography.

---

# 68. VISUAL QA

Verify:

- Consistent spacing.
- Typography hierarchy.
- Image ratios.
- Button consistency.
- Card consistency.
- Section rhythm.
- Header behavior.
- Hover states.
- Focus states.
- Loading states.
- Empty states.
- Error states.
- Mobile layout.
- Desktop layout.
- Large-screen layout.

---

# 69. PERFORMANCE QA

Verify:

```text
No unnecessary dependencies
No excessive JavaScript
No layout shifts
No giant images
No blocking animations
No unnecessary API calls
No horizontal overflow
```

Animations should preferably use:

```text
transform
opacity
```

---

# 70. FINAL ACCEPTANCE CRITERIA

CLASSIC is complete only when:

### Visual

- [ ] Looks premium.
- [ ] Looks commercially viable.
- [ ] Has recognizable CLASSIC identity.
- [ ] Does not look generic.
- [ ] Has professional typography.
- [ ] Has excellent spacing.
- [ ] Has polished cards.
- [ ] Has polished header.
- [ ] Has polished product detail.
- [ ] Has polished checkout presentation.

### Motion

- [ ] Hero transitions work.
- [ ] Product hover works.
- [ ] Product image transitions work.
- [ ] Carousels work.
- [ ] Scroll reveal works.
- [ ] Header transition works.
- [ ] Cart feedback works.
- [ ] Drawers work.
- [ ] Modals work.
- [ ] Reduced motion works.

### UX

- [ ] Navigation is obvious.
- [ ] Search is accessible.
- [ ] Products are easy to discover.
- [ ] Prices are clear.
- [ ] CTAs are obvious.
- [ ] Checkout is distraction-free.
- [ ] Mobile experience is excellent.
- [ ] Touch interactions work.

### Technical

- [ ] Existing APIs preserved.
- [ ] Existing cart preserved.
- [ ] Existing checkout preserved.
- [ ] Existing payments preserved.
- [ ] Existing authentication preserved.
- [ ] Existing orders preserved.
- [ ] Tenant isolation preserved.
- [ ] No secrets exposed.
- [ ] No fake features.
- [ ] Build passes.
- [ ] Tests pass when available.
- [ ] Lint passes when available.
- [ ] Typecheck passes when available.

---

# 71. FINAL DIRECTIVE

Do not interpret this prompt as:

> "Make a nice ecommerce page."

Interpret it as:

> **Build the flagship commercial ecommerce storefront experience of Qynex.**

CLASSIC must be:

```text
PROFESSIONAL
PREMIUM
FAST
RESPONSIVE
INTERACTIVE
CONVERSION-FOCUSED
ACCESSIBLE
DATA-DRIVEN
DISTINCTIVE
```

Use intelligently:

```text
CAROUSELS
SLIDERS
SWIPE
HORIZONTAL SCROLLING
STICKY HEADER
STICKY CTA
PRODUCT GALLERY
SCROLL REVEAL
MICROINTERACTIONS
HOVER STATES
SKELETONS
DRAWERS
BOTTOM SHEETS
MODALS
CROSSFADE
SUBTLE PARALLAX
TRANSITIONS
```

But never add an effect simply because it looks impressive.

Every interaction must answer at least one question:

```text
Does it improve understanding?
Does it improve navigation?
Does it improve discovery?
Does it improve trust?
Does it improve conversion?
```

If the answer is no:

**remove it.**

The storefront should feel alive.

It should not feel static.

It should not feel overloaded.

It should not feel like a template marketplace.

It should feel like:

# A PREMIUM COMMERCIAL ECOMMERCE PRODUCT READY TO BE SOLD TO REAL BUSINESSES.

Finally:

**Business logic is authoritative.**

**CLASSIC is presentation.**

**The existing ecommerce engine remains the source of truth.**