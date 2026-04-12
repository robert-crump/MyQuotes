package com.example.myquotes;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

/*This is my custom ViewModelStoreOwner class. This class holds the ViewModelStore and manages the
creation and retrieval of ViewModels.*/
public class MyAppViewModelStoreOwner implements ViewModelStoreOwner {
    private final ViewModelStore viewModelStore = new ViewModelStore();

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return viewModelStore;
    }
}