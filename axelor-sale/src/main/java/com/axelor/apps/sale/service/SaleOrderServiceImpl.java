/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.sale.service;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import com.axelor.apps.sale.db.CancelReason;
import com.google.common.base.Strings;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.AppSale;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.team.db.Team;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.sale.db.ISaleOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.IExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderServiceImpl implements SaleOrderService {

	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	protected SaleOrderLineService saleOrderLineService;
	protected SaleOrderLineTaxService saleOrderLineTaxService;
	protected SequenceService sequenceService;
	protected PartnerService partnerService;
	protected PartnerRepository partnerRepo;
	protected SaleOrderRepository saleOrderRepo;
	protected AppSaleService appSaleService;
	protected User currentUser;
	
	protected LocalDate today;
	
	@Inject
	public SaleOrderServiceImpl(SaleOrderLineService saleOrderLineService, SaleOrderLineTaxService saleOrderLineTaxService, SequenceService sequenceService,
			PartnerService partnerService, PartnerRepository partnerRepo, SaleOrderRepository saleOrderRepo, AppSaleService appSaleService, UserService userService)  {
		
		this.saleOrderLineService = saleOrderLineService;
		this.saleOrderLineTaxService = saleOrderLineTaxService;
		this.sequenceService = sequenceService;
		this.partnerService = partnerService;
		this.partnerRepo = partnerRepo;
		this.saleOrderRepo = saleOrderRepo;
		this.appSaleService = appSaleService;

		this.today = appSaleService.getTodayDate();
		this.currentUser = userService.getUser();
	}
	

	@Override
	public SaleOrder _computeSaleOrderLineList(SaleOrder saleOrder) throws AxelorException  {

		if(saleOrder.getSaleOrderLineList() != null)  {
			for(SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList())  {
				saleOrderLine.setCompanyExTaxTotal(saleOrderLineService.getAmountInCompanyCurrency(saleOrderLine.getExTaxTotal(), saleOrder));
			}
		}

		return saleOrder;
	}


	@Override
	public SaleOrder computeSaleOrder(SaleOrder saleOrder) throws AxelorException  {
		
		AppSale appSale = Beans.get(AppSaleService.class).getAppSale();
		if (appSale != null && appSale.getActive() && appSale.getProductPackMgt()) {
			this._addPackLines(saleOrder);
		}
		
		this.initSaleOrderLineTaxList(saleOrder);

		this._computeSaleOrderLineList(saleOrder);

		this._populateSaleOrder(saleOrder);

		this._computeSaleOrder(saleOrder);

		return saleOrder;
	}
	
	@Override
	public void computeMarginSaleOrder(SaleOrder saleOrder) {

		if (saleOrder.getSaleOrderLineList() == null) {
			
			saleOrder.setTotalCostPrice(BigDecimal.ZERO);
			saleOrder.setTotalGrossMargin(BigDecimal.ZERO);
			saleOrder.setMarginRate(BigDecimal.ZERO);
		} else {
			
			BigDecimal totalCostPrice = BigDecimal.ZERO;
			BigDecimal totalGrossMargin = BigDecimal.ZERO;
			BigDecimal marginRate = BigDecimal.ZERO;

			for (SaleOrderLine saleOrderLineList : saleOrder.getSaleOrderLineList()) {
				
				if (saleOrderLineList.getProduct() == null
						|| saleOrderLineList.getSubTotalCostPrice().compareTo(BigDecimal.ZERO) == 0
						|| saleOrderLineList.getExTaxTotal().compareTo(BigDecimal.ZERO) == 0) {
					continue;
				} else {
					
					totalCostPrice = totalCostPrice.add(saleOrderLineList.getSubTotalCostPrice());
					totalGrossMargin = totalGrossMargin.add(saleOrderLineList.getSubTotalGrossMargin());
					marginRate = totalGrossMargin.divide(totalCostPrice, RoundingMode.HALF_EVEN).multiply(new BigDecimal(100));
				}
			}
			saleOrder.setTotalCostPrice(totalCostPrice);
			saleOrder.setTotalGrossMargin(totalGrossMargin);
			saleOrder.setMarginRate(marginRate);
		}
	}


	/**
	 * Peupler un devis.
	 * <p>
	 * Cette fonction permet de déterminer les tva d'un devis.
	 * </p>
	 *
	 * @param saleOrder
	 *
	 * @throws AxelorException
	 */
	@Override
	public void _populateSaleOrder(SaleOrder saleOrder) throws AxelorException {

		logger.debug("Peupler un devis => lignes de devis: {} ", new Object[] { saleOrder.getSaleOrderLineList().size() });

		// create Tva lines
		saleOrder.getSaleOrderLineTaxList().addAll(saleOrderLineTaxService.createsSaleOrderLineTax(saleOrder, saleOrder.getSaleOrderLineList()));

	}

	/**
	 * Compute the sale order total amounts
	 *
	 * @param invoice
	 * @param vatLines
	 * @throws AxelorException
	 */
	@Override
	public void _computeSaleOrder(SaleOrder saleOrder) throws AxelorException {

		saleOrder.setExTaxTotal(BigDecimal.ZERO);
		saleOrder.setTaxTotal(BigDecimal.ZERO);
		saleOrder.setInTaxTotal(BigDecimal.ZERO);
		
		for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
			saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add( saleOrderLine.getExTaxTotal() ));
			
			// In the company accounting currency
			saleOrder.setCompanyExTaxTotal(saleOrder.getCompanyExTaxTotal().add( saleOrderLine.getCompanyExTaxTotal() ));
		}

		for (SaleOrderLineTax saleOrderLineVat : saleOrder.getSaleOrderLineTaxList()) {

			// In the sale order currency
			saleOrder.setTaxTotal(saleOrder.getTaxTotal().add( saleOrderLineVat.getTaxTotal() ));

		}
		
		saleOrder.setInTaxTotal(saleOrder.getExTaxTotal().add(saleOrder.getTaxTotal()));

		logger.debug("Montant de la facture: HTT = {},  HT = {}, Taxe = {}, TTC = {}",
				new Object[] { saleOrder.getExTaxTotal(), saleOrder.getTaxTotal(), saleOrder.getInTaxTotal() });

	}


	/**
	 * Permet de réinitialiser la liste des lignes de TVA
	 * @param saleOrder
	 * 			Un devis
	 */
	@Override
	public void initSaleOrderLineTaxList(SaleOrder saleOrder) {

		if (saleOrder.getSaleOrderLineTaxList() == null) { saleOrder.setSaleOrderLineTaxList(new ArrayList<SaleOrderLineTax>()); }

		else { saleOrder.getSaleOrderLineTaxList().clear(); }

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Partner validateCustomer(SaleOrder saleOrder)  {

		Partner clientPartner = partnerRepo.find(saleOrder.getClientPartner().getId());
		clientPartner.setIsCustomer(true);
		clientPartner.setIsProspect(false);

		return partnerRepo.save(clientPartner);
	}



	@Override
	public String getSequence(Company company) throws AxelorException  {

		String seq = sequenceService.getSequenceNumber(IAdministration.SALES_ORDER, company);
		if (seq == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.SALES_ORDER_1),company.getName()),
							IException.CONFIGURATION_ERROR);
		}
		return seq;
	}


	@Override
	public SaleOrder createSaleOrder(Company company) throws AxelorException{
		SaleOrder saleOrder = new SaleOrder();
		saleOrder.setCreationDate(appSaleService.getTodayDate());
		if(company != null){
			saleOrder.setCompany(company);
			saleOrder.setSaleOrderSeq(this.getSequence(company));
			saleOrder.setCurrency(company.getCurrency());
		}
		saleOrder.setSalemanUser(AuthUtils.getUser());
		saleOrder.setTeam(saleOrder.getSalemanUser().getActiveTeam());
		saleOrder.setStatusSelect(ISaleOrder.STATUS_DRAFT);
		this.computeEndOfValidityDate(saleOrder);
		return saleOrder;
	}

	@Override
	public SaleOrder createSaleOrder(User salemanUser, Company company, Partner contactPartner, Currency currency,
			LocalDate deliveryDate, String internalReference, String externalReference, LocalDate orderDate,
			PriceList priceList, Partner clientPartner, Team team) throws AxelorException  {

		logger.debug("Création d'un devis client : Société = {},  Reference externe = {}, Client = {}",
				new Object[] { company, externalReference, clientPartner.getFullName() });

		SaleOrder saleOrder = new SaleOrder();
		saleOrder.setClientPartner(clientPartner);
		saleOrder.setCreationDate(appSaleService.getTodayDate());
		saleOrder.setContactPartner(contactPartner);
		saleOrder.setCurrency(currency);
		saleOrder.setExternalReference(externalReference);
		saleOrder.setOrderDate(orderDate);

		if(salemanUser == null)  {
			salemanUser = AuthUtils.getUser();
		}
		saleOrder.setSalemanUser(salemanUser);

		if(team == null)  {
			team = salemanUser.getActiveTeam();
		}

		if(company == null)  {
			company = salemanUser.getActiveCompany();
		}

		saleOrder.setCompany(company);
		saleOrder.setMainInvoicingAddress(partnerService.getInvoicingAddress(clientPartner));
		saleOrder.setDeliveryAddress(partnerService.getDeliveryAddress(clientPartner));
		
		if(priceList == null)  {
			priceList = clientPartner.getSalePriceList();
		}

		saleOrder.setPriceList(priceList);

		saleOrder.setSaleOrderLineList(new ArrayList<SaleOrderLine>());

		saleOrder.setSaleOrderSeq(this.getSequence(company));
		saleOrder.setStatusSelect(ISaleOrder.STATUS_DRAFT);

		this.computeEndOfValidityDate(saleOrder);

		return saleOrder;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancelSaleOrder(SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr){
		Query q = JPA.em().createQuery("select count(*) FROM SaleOrder as self WHERE self.statusSelect = ?1 AND self.clientPartner = ?2 ");
		q.setParameter(1, ISaleOrder.STATUS_ORDER_CONFIRMED);
		q.setParameter(2, saleOrder.getClientPartner());
		if((long) q.getSingleResult() == 1)  {
			saleOrder.getClientPartner().setIsCustomer(false);
			saleOrder.getClientPartner().setIsProspect(true);
		}
		saleOrder.setStatusSelect(ISaleOrder.STATUS_CANCELED);
		saleOrder.setCancelReason(cancelReason);
		if (Strings.isNullOrEmpty(cancelReasonStr)) {
			saleOrder.setCancelReasonStr(cancelReason.getName());
		} else {
			saleOrder.setCancelReasonStr(cancelReasonStr);
		}
		saleOrderRepo.save(saleOrder);
	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	protected void _finalizeSaleOrder(SaleOrder saleOrder) throws Exception {
		saleOrder.setStatusSelect(ISaleOrder.STATUS_FINALIZE);
		saleOrderRepo.save(saleOrder);
		if (appSaleService.getAppSale().getManageSaleOrderVersion()){
			this.saveSaleOrderPDFAsAttachment(saleOrder);
		}
		if (saleOrder.getVersionNumber() == 1){
			saleOrder.setSaleOrderSeq(this.getSequence(saleOrder.getCompany()));
		}
	}

	@Override
	public void finalizeSaleOrder(SaleOrder saleOrder) throws Exception {
		_finalizeSaleOrder(saleOrder);
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void confirmSaleOrder(SaleOrder saleOrder) throws Exception  {
		saleOrder.setStatusSelect(ISaleOrder.STATUS_ORDER_CONFIRMED);
		saleOrder.setConfirmationDate(this.today);
		saleOrder.setConfirmedByUser(this.currentUser);
		
		this.validateCustomer(saleOrder);
		
		saleOrderRepo.save(saleOrder);
	}
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void finishSaleOrder(SaleOrder saleOrder) throws AxelorException {
		saleOrder.setStatusSelect(ISaleOrder.STATUS_FINISHED);

		saleOrderRepo.save(saleOrder);
	}
	
	@Override
	@Transactional
	public SaleOrder mergeSaleOrders(List<SaleOrder> saleOrderList, Currency currency, Partner clientPartner, Company company, Partner contactPartner, PriceList priceList, Team team) throws AxelorException {
		
		String numSeq = "";
		String externalRef = "";
		for (SaleOrder saleOrderLocal : saleOrderList) {
			if (!numSeq.isEmpty()){
				numSeq += "-";
			}
			numSeq += saleOrderLocal.getSaleOrderSeq();

			if (!externalRef.isEmpty()){
				externalRef += "|";
			}
			if (saleOrderLocal.getExternalReference() != null){
				externalRef += saleOrderLocal.getExternalReference();
			}
		}
		
		SaleOrder saleOrderMerged = this.createSaleOrder(
				AuthUtils.getUser(),
				company,
				contactPartner,
				currency,
				null,
				numSeq,
				externalRef,
				LocalDate.now(),
				priceList,
				clientPartner,
				team);
		
		this.attachToNewSaleOrder(saleOrderList,saleOrderMerged);
		
		this.computeSaleOrder(saleOrderMerged);
		
		saleOrderRepo.save(saleOrderMerged);
		
		this.removeOldSaleOrders(saleOrderList);
		
		return saleOrderMerged;
	}

	//Attachment of all sale order lines to new sale order
	public void attachToNewSaleOrder(List<SaleOrder> saleOrderList, SaleOrder saleOrderMerged) {
		for(SaleOrder saleOrder : saleOrderList)  {
			int countLine = 1;
			for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
				saleOrderLine.setSequence(countLine * 10);
				saleOrderMerged.addSaleOrderLineListItem(saleOrderLine);
				countLine++;
			}
		}
	}
	
	//Remove old sale orders after merge
	public void removeOldSaleOrders(List<SaleOrder> saleOrderList) {
		for(SaleOrder saleOrder : saleOrderList)  {
			saleOrderRepo.remove(saleOrder);
		}
	}
	
	@Override
	public void saveSaleOrderPDFAsAttachment(SaleOrder saleOrder) throws AxelorException  {
		
		String language = this.getLanguageForPrinting(saleOrder);
		
		ReportFactory.createReport(IReport.SALES_ORDER, this.getFileName(saleOrder)+"-${date}")
				.addParam("Locale", language)
				.addParam("SaleOrderId", saleOrder.getId())
				.toAttach(saleOrder)
				.generate()
				.getFileLink();
		
//		String relatedModel = generalService.getPersistentClass(saleOrder).getCanonicalName(); required ?
		
	}

	@Override
	public String getLanguageForPrinting(SaleOrder saleOrder)  {
		String language="";
		try{
			language = saleOrder.getClientPartner().getLanguageSelect() != null? saleOrder.getClientPartner().getLanguageSelect() : saleOrder.getCompany().getPrintingSettings().getLanguageSelect() != null ? saleOrder.getCompany().getPrintingSettings().getLanguageSelect() : "en" ;
		}catch (NullPointerException e) {
			language = "en";
		}
		language = language.equals("")? "en": language;
		
		return language;
	}
	
	@Override
	public String getFileName(SaleOrder saleOrder)  {
		
		return I18n.get("Sale order") + " " + saleOrder.getSaleOrderSeq() + ((saleOrder.getVersionNumber() > 1) ? "-V" + saleOrder.getVersionNumber() : "");
	}
	
	@Override
	@Transactional
	public SaleOrder createSaleOrder(SaleOrder context){
		SaleOrder copy = saleOrderRepo.copy(context, true);
		copy.setTemplate(false);
		copy.setTemplateUser(null);
		return copy;
	}

	@Override
	@Transactional
	public SaleOrder createTemplate(SaleOrder context){
		SaleOrder copy = saleOrderRepo.copy(context, true);
		copy.setTemplate(true);
		copy.setTemplateUser(AuthUtils.getUser());
		return copy;
	}


	@Override
	public SaleOrder computeEndOfValidityDate(SaleOrder saleOrder)  {

		saleOrder.setEndOfValidityDate(
				Beans.get(DurationService.class).computeDuration(saleOrder.getDuration(), saleOrder.getCreationDate()));

		return saleOrder;

	}
	
	@Override
	public String getReportLink(SaleOrder saleOrder, String name, String language, String format) throws AxelorException{

		return ReportFactory.createReport(IReport.SALES_ORDER, name+"-${date}")
		.addParam("Locale", language)
		.addParam("SaleOrderId", saleOrder.getId())
		.addFormat(format)
		.generate()
		.getFileLink();
	}
	
	private void _addPackLines(SaleOrder saleOrder) {
		
		if (saleOrder.getSaleOrderLineList() == null) {
			return;
		}
		
		List<SaleOrderLine> lines = new ArrayList<SaleOrderLine>();
		lines.addAll(saleOrder.getSaleOrderLineList());
		for (SaleOrderLine line : lines) {
			if (line.getSubLineList() == null) {
				continue;
			}
			for (SaleOrderLine subLine : line.getSubLineList()) {
				if (subLine.getSaleOrder() == null) {
					saleOrder.addSaleOrderLineListItem(subLine);
				}
			}
		}
	}
}



