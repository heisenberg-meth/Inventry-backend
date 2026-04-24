package com.ims.product;

public class CategoryCount {
  private String categoryName;
  private Long productCount;

  public CategoryCount() {
    // no-args constructor for JPA / Jackson
  }

  public CategoryCount(String categoryName, Long productCount) {
    this.categoryName = categoryName;
    this.productCount = productCount;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
  }

  public Long getProductCount() {
    return productCount;
  }

  public void setProductCount(Long productCount) {
    this.productCount = productCount;
  }
}
