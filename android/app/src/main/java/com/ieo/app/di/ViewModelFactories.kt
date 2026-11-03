package com.ieo.app.di

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.ieo.app.IeoApplication

/** ViewModel 팩토리 안에서 [AppContainer]를 꺼낸다: `initializer { MyViewModel(appContainer().xxx) }` */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as IeoApplication).container
