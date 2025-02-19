/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.sheets;

import static com.itsaky.androidide.utils.AttributeDialogs.booleanEditor;
import static com.itsaky.androidide.utils.AttributeDialogs.colorPicker;
import static com.itsaky.androidide.utils.AttributeDialogs.enumEditor;
import static com.itsaky.androidide.utils.AttributeDialogs.flagEditor;
import static com.itsaky.attrinfo.models.Attr.BOOLEAN;
import static com.itsaky.attrinfo.models.Attr.COLOR;
import static com.itsaky.attrinfo.models.Attr.DIMENSION;
import static com.itsaky.attrinfo.models.Attr.ENUM;
import static com.itsaky.attrinfo.models.Attr.FLAG;
import static com.itsaky.attrinfo.models.Attr.STRING;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.transition.TransitionManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.itsaky.androidide.R;
import com.itsaky.androidide.adapters.SimpleIconTextAdapter;
import com.itsaky.androidide.adapters.XMLAttributeListAdapter;
import com.itsaky.androidide.app.StudioApp;
import com.itsaky.androidide.colorpicker.ColorPickerView;
import com.itsaky.androidide.databinding.LayoutAttrEditorSheetBinding;
import com.itsaky.androidide.databinding.LayoutAttrEditorSheetItemBinding;
import com.itsaky.androidide.models.IconTextListItem;
import com.itsaky.androidide.models.XMLAttribute;
import com.itsaky.androidide.utils.AttributeDialogs;
import com.itsaky.androidide.utils.DialogUtils;
import com.itsaky.androidide.utils.Logger;
import com.itsaky.attrinfo.models.Attr;
import com.itsaky.inflater.IAttribute;
import com.itsaky.inflater.IView;
import com.itsaky.inflater.impl.UiAttribute;
import com.itsaky.toaster.Toaster;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class AttrEditorSheet extends BottomSheetDialogFragment implements SimpleIconTextAdapter.OnBindListener<IconTextListItem> {
    
    private static final List<IconTextListItem> VIEW_ACTIONS = new ArrayList<> ();
    private static final Logger LOG = Logger.instance ("AttrBottomSheet");
    private AttributeListSheet mAttrListSheet;
    private IView selectedView;
    private File layout;
    private LayoutAttrEditorSheetBinding binding;
    private OnViewDeletionFailedListener mDeletionFailedListener;
    
    @Nullable
    @Override
    public View onCreateView (@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = LayoutAttrEditorSheetBinding.inflate (LayoutInflater.from (getContext ()), container, false);
        return binding.getRoot ();
    }
    
    @Override
    public void onViewCreated (@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated (view, savedInstanceState);
        
        if (VIEW_ACTIONS.isEmpty ()) {
            Collections.addAll (VIEW_ACTIONS,
                    IconTextListItem.create (getString (R.string.msg_viewaction_add_attr), R.drawable.ic_add),
                    IconTextListItem.create (getString (R.string.title_viewaction_delete), R.drawable.ic_delete),
                    IconTextListItem.create (getString (R.string.msg_viewaction_select_parent), R.drawable.ic_view_select_parent)
            );
        }
        
        binding.valueEditorLayout.closeButton.setOnClickListener (v -> hideValueEditorLayout ());
        
        setupViewData ();
        
        AttributeDialogs.init (requireActivity ());
    }
    
    @Override
    public void onDismiss (@NonNull DialogInterface dialog) {
        AttributeDialogs.release ();
        super.onDismiss (dialog);
    }
    
    public AttrEditorSheet setDeletionFailedListener (OnViewDeletionFailedListener listener) {
        this.mDeletionFailedListener = listener;
        return this;
    }
    
    public AttrEditorSheet setLayout (File layout) {
        Objects.requireNonNull (layout);
        
        this.layout = layout;
        return this;
    }
    
    private void setupViewData () {
        binding.actionsList.setAdapter (new SimpleIconTextAdapter (VIEW_ACTIONS).setOnBindListener (this));
        
        if (this.selectedView == null) {
            LOG.error ("Cannot edit attributes of a null view.");
            return;
        }
        
        binding.widgetName.setText (this.selectedView.getXmlTag ());
        binding.attrList.setAdapter (new XMLAttributeListAdapter (
                this.selectedView.getAttributes ()
                        .stream ()
                        .map (XMLAttribute::new)
                        .collect (Collectors.toList ()), this::onAttrClick)
        );
    }
    
    public void setSelectedView (IView view) {
        this.selectedView = view;
    }
    
    private void onAttrClick (LayoutAttrEditorSheetItemBinding binding, @NonNull XMLAttribute attribute) {
        
        if (this.selectedView == null) {
            LOG.error ("Cannot edit attributes of a null view.");
            return;
        }
        
        final var format = attribute.findFormat ();
        if (format == -1) {
            StudioApp.getInstance ().toast (getString (R.string.msg_no_attr_format), Toaster.Type.ERROR);
            LOG.error (getString (R.string.msg_no_attr_format), attribute);
            return;
        }
        
        showEditorDialog (attribute);
    }
    
    private void hideValueEditorLayout () {
        TransitionManager.beginDelayedTransition (binding.getRoot ());
        binding.valueEditorLayout.getRoot ().setVisibility (View.GONE);
    }
    
    private void showEditorDialog (@NonNull XMLAttribute attribute) {
        final var attr = attribute.getAttr ();
        final var values = attr.possibleValues.toArray (new String[0]);
        final AttributeDialogs.OnClickListener onDone = (dialog, which, newValue) -> applyNewValue (attribute, newValue);
        
        final ColorPickerView.OnPickListener mPickListener = (color, hexCode) -> applyNewValue (attribute, hexCode);
        
        AlertDialog dialog = null;
        if (attribute.hasFormat (DIMENSION)) {
            dialog = AttributeDialogs.dimensionEditor (attribute.getValue (), onDone);
        } else if (attribute.hasFormat (STRING)) {
            dialog = AttributeDialogs.stringEditor (onDone);
        } else if (attribute.hasFormat (BOOLEAN)) {
            dialog = booleanEditor (onDone);
        } else if (attribute.hasFormat (ENUM)) {
            dialog = enumEditor (values, attribute.getValue (), onDone);
        } else if (attribute.hasFormat (FLAG)) {
            dialog = flagEditor (values, attribute.getValue (), onDone);
        } else if (attribute.hasFormat (COLOR)) {
            // TODO Implement something to allow values from colors.xml or ColorStateLists
            //    Make this color picker optional
            dialog = colorPicker (mPickListener);
        }
        
        if (dialog != null) {
            dialog.show ();
        }
    }
    
    private void applyNewValue (@NonNull IAttribute attribute, String newValue) {
        if (this.selectedView.hasAttribute (attribute.getNamespace (), attribute.getAttributeName ())) {
            if (!this.selectedView.updateAttribute (attribute.getNamespace (), attribute.getAttributeName (), newValue)) {
                StudioApp.getInstance ().toast (getString (R.string.msg_attr_not_updated), Toaster.Type.ERROR);
            } else {
                // Update the view data
                // This will make sure that the attributes list has been updated
                setupViewData ();
            }
        } else {
            addAttribute (new UiAttribute (attribute.getNamespace (), attribute.getAttributeName (), newValue));
        }
    }
    
    private void addAttribute (@NonNull IAttribute attribute) {
        this.selectedView.addAttribute (attribute);
        setupViewData ();
        StudioApp.getInstance ().toast (getString (R.string.msg_attr_added), Toaster.Type.SUCCESS);
    }
    
    @Override
    public void postBind (IconTextListItem item, @NonNull SimpleIconTextAdapter.VH holder, int position) {
        final var binding = holder.binding;
        binding.getRoot ().setOnClickListener (v -> onViewActionClick (position));
    }
    
    private void onViewActionClick (int position) {
        if (this.selectedView == null) {
            return;
        }
        
        if (position == 0) { // Add attribute
            var tag = this.selectedView.getXmlTag ();
            if ("include".equals (tag) || "merge".equals (tag)) {
                tag = "View";
            }
            
            final var attrs = StudioApp.getInstance ().attrInfo ();
            final var style = attrs.getStyle (tag);
            if (style == null) {
                LOG.error ("Unable to retrieve attributes for tag:", tag);
                return;
            }
            
            final var attributes = new TreeSet<Attr> (Comparator.comparing (attr -> attr.name));
            attributes.addAll (style.attributes);
            
            final var widgetInfo = StudioApp.getInstance ().widgetInfo ();
            final var widget = widgetInfo.getWidgetBySimpleName (tag);
            if (widget != null) {
                for (var superclass : widget.superclasses) {
                    if ("java.lang.Object".equals (superclass)) {
                        break;
                    }
                    
                    final var simpleName = superclass.substring (superclass.lastIndexOf (".") + 1);
                    final var superStyle = attrs.getStyle (simpleName);
                    if (superStyle != null) {
                        attributes.addAll (superStyle.attributes);
                    }
                }
            }
            
            final var parent = this.selectedView.getParent ();
            if (parent != null) {
                final var parentTag = parent.getXmlTag ();
                final var parentWidget = widgetInfo.getWidgetBySimpleName (parentTag);
                if (parentWidget != null) {
                    final var parentLayoutParams = attrs.getStyle (parentTag + "_Layout");
                    if (parentLayoutParams != null) {
                        attributes.addAll (parentLayoutParams.attributes);
                    }
                    
                    final var paramSuperclasses = widgetInfo.getLayoutParamSuperClasses (parentWidget.name);
                    if (paramSuperclasses != null) {
                        for (var superclass : paramSuperclasses) {
                            if ("java.lang.Object".equals (superclass)) {
                                continue;
                            }
                            
                            final var split = superclass.split ("\\.");
                            final var simpleClassName = split[split.length - 2];
                            var paramName = split[split.length - 1];
                            paramName = paramName.substring (0, paramName.length () - "Params".length ());
                            final var superParamEntry = attrs.getStyle (simpleClassName + "_" + paramName);
                            if (superParamEntry != null) {
                                attributes.addAll (superParamEntry.attributes);
                            }
                        }
                    }
                }
            }
            
            final var sheet = getAttrListSheet ();
            sheet.setItems (filterAppliedAttributes (attributes));
            sheet.show (getChildFragmentManager (), "attr_list_sheet");
            
        } else if (position == 1) { // Delete
            DialogUtils.newYesNoDialog (getContext (), (dialog, which) -> {
                var handled = selectedView.removeFromParent ();
                if (!handled) {
                    handled = mDeletionFailedListener != null && mDeletionFailedListener.onDeletionFailed (this.selectedView);
                }
                if (!handled) {
                    StudioApp.getInstance ().toast (getString (R.string.msg_view_deletion_failed), Toaster.Type.ERROR);
                } else {
                    dismiss ();
                }
            }, (dialog, which) -> dialog.dismiss ()).show ();
        } else if (position == 2) { // Select parent
            if (this.selectedView.getParent () == null) {
                StudioApp.getInstance ().toast (getString (R.string.msg_no_view_parent), Toaster.Type.ERROR);
                return;
            }
            
            this.selectedView = this.selectedView.getParent ();
            
            TransitionManager.beginDelayedTransition (binding.getRoot ());
            setupViewData ();
        }
    }
    
    @NonNull
    @Contract("_ -> new")
    private List<Attr> filterAppliedAttributes (@NonNull TreeSet<Attr> attributes) {
        attributes.removeIf (attr -> this.selectedView.hasAttribute (attr.namespace, attr.name));
        return new ArrayList<> (attributes);
    }
    
    @NonNull
    private AttributeListSheet getAttrListSheet () {
        if (mAttrListSheet == null) {
            mAttrListSheet = new AttributeListSheet ();
            mAttrListSheet.onItemClick (this::addNewAttribute);
            mAttrListSheet.setCancelable (true);
        }
        
        return mAttrListSheet;
    }
    
    private void addNewAttribute (@NonNull Attr attr) {
        final XMLAttribute attribute = new XMLAttribute (attr.namespace, attr.name, "", false);
        attribute.setAttr (attr);
        showEditorDialog (attribute);
    }
    
    /**
     * A listener can be used to get notified when we fail to remove
     * a view from its parent. This is used in {@link com.itsaky.androidide.DesignerActivity DesignerActivity}.
     * <p>
     * When the user tries to remove the outermost view of the inflated layout,
     * AttrEditorSheet fails to remove the view from its parent ({@link IView#getParent()} is null for root XML layout).
     * In this case, DesignerActivity check if the view that we were trying to delete is the root layout or not.
     * If it is the root layout, then it deletes the layout from the layout container.
     *
     * @author Akash Yadav
     */
    public interface OnViewDeletionFailedListener {
        
        /**
         * @param view The view that was not removed from its parent.
         * @return {@code true} if the listener handled the error. {@code false} otherwise.
         */
        boolean onDeletionFailed (IView view);
    }
}