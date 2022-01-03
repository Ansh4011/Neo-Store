package com.looker.droidify.ui.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.looker.droidify.database.DatabaseX
import com.looker.droidify.database.Product
import com.looker.droidify.entity.ProductItem
import com.looker.droidify.ui.fragments.Request
import com.looker.droidify.ui.fragments.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainNavFragmentViewModelX(val db: DatabaseX) : ViewModel() {

    private val _order = MutableStateFlow(ProductItem.Order.LAST_UPDATE)
    private val _sections = MutableStateFlow<ProductItem.Section>(ProductItem.Section.All)
    private val _searchQuery = MutableStateFlow("")

    val order: StateFlow<ProductItem.Order> = _order.stateIn(
        initialValue = ProductItem.Order.LAST_UPDATE,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val sections: StateFlow<ProductItem.Section> = _sections.stateIn(
        initialValue = ProductItem.Section.All,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )
    val searchQuery: StateFlow<String> = _searchQuery.stateIn(
        initialValue = "",
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun request(source: Source): Request {
        var mSearchQuery = ""
        var mSections: ProductItem.Section = ProductItem.Section.All
        var mOrder: ProductItem.Order = ProductItem.Order.NAME
        viewModelScope.launch {
            launch { searchQuery.collect { if (source.sections) mSearchQuery = it } }
            launch { sections.collect { if (source.sections) mSections = it } }
            launch { order.collect { if (source.order) mOrder = it } }
        }
        return when (source) {
            Source.AVAILABLE -> Request.ProductsAvailable(
                mSearchQuery,
                mSections,
                mOrder
            )
            Source.INSTALLED -> Request.ProductsInstalled(
                mSearchQuery,
                mSections,
                mOrder
            )
            Source.UPDATES -> Request.ProductsUpdates(
                mSearchQuery,
                mSections,
                mOrder
            )
        }
    }

    var productsList = MediatorLiveData<MutableList<Product>>()

    fun fillList(source: Source) {
        viewModelScope.launch {
            productsList.value = query(request(source))?.toMutableList()
        }
    }

    private suspend fun query(request: Request): List<Product>? {
        return withContext(Dispatchers.IO) {
            when (request) {
                is Request.ProductsAvailable -> db.productDao
                    .queryList(
                        installed = false,
                        updates = false,
                        searchQuery = request.searchQuery,
                        section = request.section,
                        order = request.order
                    )
                is Request.ProductsInstalled -> db.productDao
                    .queryList(
                        installed = true,
                        updates = false,
                        searchQuery = request.searchQuery,
                        section = request.section,
                        order = request.order
                    )
                is Request.ProductsUpdates -> db.productDao
                    .queryList(
                        installed = true,
                        updates = true,
                        searchQuery = request.searchQuery,
                        section = request.section,
                        order = request.order
                    )
                else -> listOf()
            }
        }
    }

    fun setSection(newSection: ProductItem.Section, perform: () -> Unit) {
        viewModelScope.launch {
            if (newSection != sections.value) {
                _sections.emit(newSection)
                launch(Dispatchers.Main) { perform() }
            }
        }
    }

    fun setOrder(newOrder: ProductItem.Order, perform: () -> Unit) {
        viewModelScope.launch {
            if (newOrder != order.value) {
                _order.emit(newOrder)
                launch(Dispatchers.Main) { perform() }
            }
        }
    }

    fun setSearchQuery(newSearchQuery: String, perform: () -> Unit) {
        viewModelScope.launch {
            if (newSearchQuery != searchQuery.value) {
                _searchQuery.emit(newSearchQuery)
                launch(Dispatchers.Main) { perform() }
            }
        }
    }

    class Factory(val db: DatabaseX) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainNavFragmentViewModelX::class.java)) {
                return MainNavFragmentViewModelX(db) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
