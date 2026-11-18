<#macro content>
  <#if client?? && client.baseUrl?has_content>
    <#assign languageTag = (locale.currentLanguageTag)!"en">
    <#assign storeLocale = (languageTag == "zh-CN")?then("zh-CN", "en")>
    <a class="bookstore-back" href="${client.baseUrl}/${storeLocale}/books">${msg("backToBookstore")}</a>
  </#if>
</#macro>
