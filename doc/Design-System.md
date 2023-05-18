# Tusky Design System

## Synopsis

A design system is "a complete set of standards intended to manage design at scale using reusable components and patterns" ([Design Systems 101](https://www.nngroup.com/articles/design-systems-101/)).

These are guidelines for creating Android layouts for Tusky that look consistent through the application.

> **Note**: Examples in this text use literal dimensions like `8dp` for simplicity. In practice you should use `@dimen/...` values from `resources/values/dimens.xml`.

## Jetpack Compose

We don't use [Jetpack Compose](https://developer.android.com/jetpack/compose) yet. That may change in the future. For now, layouts are specified as XML resources.

## Margin and padding

### Which one to use

Use `margin*` attributes if the view should have additional space around it and the space between the views is not tappable.

For example, use `margin*` between two `ImageView` where the "gutter" between them is not tappable.

Use `padding*` attributes if the user should be able to interact with the space around the content of the view.

For example, use `padding` in a `TextView` if the height of the text is smaller than the recommended `48dp` tappable area, and you want to make the space immediately above and below the text tappable.

> Note: If the goal is to ensure that the view's minimum size meets accessibility guidelines then a better choice is to set the view's `minHeight` or `minWidth` properties instead.

### Where to apply `margin*`

Margin should generally be applied to **only the top and start** of a view. A given view should "know" what is above or before its start in the layout, and it should adjust its margins relative to those things. The views that are below it or after its end are not its concern.

I.e., if a view knows it wants an 8dp margin between itself and the view above, it should set `marginTop`. It should not rely on either

1. the view above setting `marginBottom`, or
2. setting `marginTop` to `4dp` and the view above setting `marginBottom` to `4dp`.

This makes it much easier to reason about the layout of the views.

### Apply `padding*` on all view sides

Padding should generally be applied to all four sides of the view.

The values are often, but not always, symmetric between opposite sides. I.e., if `paddingTop` is `4dp` then `paddingBottom` should also be `4dp`. This keeps the content center-aligned within the view's area.

## Use dimension resources

Create layouts that specify dimensions (sizes, padding, margins, etc) using dimensions from `res/values/dimens.xml`.

This ensures that views within different layouts have consistent spacing.

Existing uses of literal dimensions have been grandfathered in, and will be adjusted over time.

New uses are prevented with a lint rule.

## Exceptions

Like all rules sometimes there are exceptions.

### Container ViewGroup interior padding

The outermost `ViewGroup` in a layout intended to be used in a list should set padding using the `listPreferredItemPadding*` values.

```
android:paddingStart="?android:attr/listPreferredItemPaddingStart"
android:paddingEnd="?android:attr/listPreferredItemPaddingEnd"
```

This is instead of the views **inside** the layout setting a `marginStart` and `marginEnd`.

Views that want to be the full width of the parent should set these to parent. If the parent needs to ensure that there is clearance between any views that may be on the left or the right (or to the edge of the screen) the view should expect that the parent has set interior padding appropriately.

I.e., do this:

```
<LinearLayout ...
  paddingStart="X"
  paddingEnd="Y">

  <SomeView ... />
</LinearLayout>
```

**not**

```
<LinearLayout>
  <SomeView
    marginStart="X"
    marginEnd="Y" />
</LinearLayout>
```
